package com.example.liuruyi.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.liuruyi.Config
import com.example.liuruyi.Prompts
import com.example.liuruyi.data.AppPrefs
import com.example.liuruyi.data.KeystoreHelper
import com.example.liuruyi.data.LocalStore
import com.example.liuruyi.data.MemoryFile
import com.example.liuruyi.data.MemoryRepository
import com.example.liuruyi.data.Message
import com.example.liuruyi.data.RelationshipFile
import com.example.liuruyi.data.SessionMeta
import com.example.liuruyi.data.SessionRepository
import com.example.liuruyi.data.SummaryFile
import com.example.liuruyi.data.Usage
import com.example.liuruyi.domain.ChatOrchestrator
import com.example.liuruyi.domain.ContextBuilder
import com.example.liuruyi.domain.ProactiveManager
import com.example.liuruyi.domain.RelationshipManager
import com.example.liuruyi.domain.Summarizer
import com.example.liuruyi.network.ApiMessage
import com.example.liuruyi.network.DeepSeekClient
import com.example.liuruyi.network.WeatherClient
import com.example.liuruyi.network.DeepSeekException
import com.example.liuruyi.util.SystemTimeProvider
import com.example.liuruyi.util.TimeProvider
import com.example.liuruyi.util.TokenEstimator
import com.example.liuruyi.util.newId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.io.File
import java.io.IOException

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val sessions: List<SessionMeta> = emptyList(),
    val activeSessionId: String = "",
    val sessionTitle: String = "",
    val memoryContent: String = "",
    val hasApiKey: Boolean = false,
    val isStreaming: Boolean = false,
    val streamingText: String = "",
    val tokenEstimate: Int = 0,
    val input: String = "",
    val error: String? = null,
    val showHardConfirm: Boolean = false,
    val hardConfirmTokens: Int = 0,
    val fullRawTokens: Int = 0,
    val proactiveCountToday: Int = 0,
    val modelName: String = Config.MODEL_NAME,
    val quoteMessage: Message? = null,
    val relationshipCloseness: Int = 35,
    val relationshipStage: String = "熟络",
    val deleteUndoActive: Boolean = false,
    val avatarPath: String = "",
    val myAvatarPath: String = "",
    val city: String = "",
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val store = LocalStore(File(application.filesDir, Config.DATA_DIR_NAME))
    private val sessionRepo = SessionRepository(store)
    private val memoryRepo = MemoryRepository(store)
    private val client = DeepSeekClient()
    private val contextBuilder = ContextBuilder()
    private val summarizer = Summarizer(client)
    private val orchestrator = ChatOrchestrator(client, contextBuilder, summarizer)
    private val proactiveManager = ProactiveManager(client)
    private val relationshipManager = RelationshipManager()
    private val timeProvider: TimeProvider = SystemTimeProvider()

    private val appContext get() = getApplication<Application>()

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var streamJob: Job? = null
    private var pendingInput: String? = null
    private var pendingStreamMessages: List<ApiMessage>? = null
    private var pendingQuote: Message? = null

    private var relationship: RelationshipFile = RelationshipFile()
    private var lastDeletedMeta: SessionMeta? = null
    private var deleteFinalizeJob: Job? = null
    private var weatherNote: String = ""

    init {
        relationship = store.readRelationship()
        refreshAll()
        refreshWeather()
    }

    // ---------------- lifecycle ----------------

    fun onAppForeground() {
        val index = sessionRepo.loadIndex()
        val reset = proactiveManager.onAppOpened(index, timeProvider.today())
        if (reset.proactive != index.proactive) sessionRepo.saveIndex(reset)
        _state.update { it.copy(proactiveCountToday = reset.proactive.sentCount) }
        maybeProactive(reset)
        refreshWeather()
    }

    // ---------------- session ops ----------------

    fun createSession() {
        sessionRepo.createSession(timeProvider.nowMillis())
        refreshAll()
    }

    fun selectSession(sessionId: String) {
        val index = sessionRepo.loadIndex()
        sessionRepo.switchSession(index, sessionId)
        _state.update { it.copy(quoteMessage = null) }
        refreshAll()
    }

    fun deleteSession(sessionId: String) {
        // 先清理上一个待撤销的快照文件
        finalizeDelete()

        val index = sessionRepo.loadIndex()
        val meta = index.sessions.firstOrNull { it.id == sessionId } ?: return
        lastDeletedMeta = meta
        sessionRepo.removeSession(index, sessionId, timeProvider.nowMillis())

        _state.update { it.copy(deleteUndoActive = true, quoteMessage = null) }
        // 6 秒后仍未撤销则物理删除文件
        deleteFinalizeJob = viewModelScope.launch {
            delay(6000)
            finalizeDelete()
        }
        refreshAll()
    }

    fun undoDeleteSession() {
        val meta = lastDeletedMeta ?: return
        deleteFinalizeJob?.cancel()
        deleteFinalizeJob = null
        sessionRepo.restoreSession(meta.id, meta)
        lastDeletedMeta = null
        _state.update { it.copy(deleteUndoActive = false) }
        refreshAll()
    }

    fun finalizeDelete() {
        deleteFinalizeJob?.cancel()
        deleteFinalizeJob = null
        lastDeletedMeta?.let { sessionRepo.deleteSessionFiles(it.id) }
        lastDeletedMeta = null
        _state.update { it.copy(deleteUndoActive = false) }
    }

    fun saveApiKey(key: String) {
        KeystoreHelper.saveApiKey(appContext, key.trim())
        _state.update { it.copy(hasApiKey = KeystoreHelper.hasApiKey(appContext)) }
    }

    fun clearApiKey() {
        KeystoreHelper.clearApiKey(appContext)
        _state.update { it.copy(hasApiKey = false) }
    }

    fun onInputChange(value: String) {
        _state.update { it.copy(input = value) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    // ---------------- 引用 / 撤回 ----------------

    fun setQuote(message: Message) {
        _state.update { it.copy(quoteMessage = if (it.quoteMessage?.id == message.id) null else message) }
    }

    fun clearQuote() {
        _state.update { it.copy(quoteMessage = null) }
    }

    fun recallMessage(messageId: String) {
        val active = _state.value.activeSessionId
        if (active.isBlank()) return
        val messages = sessionRepo.loadMessages(active)
        val idx = messages.indexOfFirst { it.id == messageId }
        if (idx < 0) return
        val updated = messages.mapIndexed { i, m ->
            if (i == idx) m.copy(content = "", recalled = true) else m
        }
        val now = timeProvider.nowMillis()
        sessionRepo.rewriteMessages(active, updated)
        val index = sessionRepo.loadIndex()
        val preview = updated.lastOrNull()?.let { if (it.recalled) "撤回了一条消息" else it.content.take(40) }.orEmpty()
        sessionRepo.saveIndex(
            index.copy(sessions = index.sessions.map { if (it.id == active) it.copy(lastMessagePreview = preview) else it }),
        )
        _state.update { it.copy(quoteMessage = null) }
        refreshAll()
    }

    fun setAvatar(uri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    appContext.contentResolver.openInputStream(uri)?.use { store.saveAvatar(it.readBytes()) }
                }
            }
            refreshAll()
        }
    }

    fun setMyAvatar(uri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    appContext.contentResolver.openInputStream(uri)?.use { store.saveMyAvatar(it.readBytes()) }
                }
            }
            refreshAll()
        }
    }

    fun setCity(city: String) {
        AppPrefs.setCity(appContext, city)
        _state.update { it.copy(city = AppPrefs.city(appContext)) }
        refreshWeather()
    }

    fun sendImage(uri: Uri) {
        val active = state.value.activeSessionId
        if (active.isBlank() || state.value.isStreaming) return
        val apiKey = KeystoreHelper.loadApiKey(appContext)
        if (apiKey.isNullOrBlank()) {
            _state.update { it.copy(error = "请先在设置页填写 DeepSeek API Key") }
            return
        }
        viewModelScope.launch {
            val id = newId()
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null) store.saveImage(id, bytes) else null
                }.getOrNull()
            }
            if (file == null) {
                _state.update { it.copy(error = "图片读取失败") }
                return@launch
            }
            val now = timeProvider.nowMillis()
            sessionRepo.appendAndUpdateIndex(
                active,
                Message(
                    id = id, role = "user", content = "[图片]", createdAt = now,
                    tokenEstimate = 0, kind = "chat", status = "complete",
                    imagePath = file.absolutePath,
                ),
                now,
            )
            refreshAll()
            try {
                val memory = memoryRepo.load()
                val summary = sessionRepo.loadSummary(active)
                val messages = sessionRepo.loadMessages(active)
                val outcome = orchestrator.prepare(
                    currentSystemPrompt(), memory.content, summary, messages, Config.IMAGE_PROMPT, apiKey,
                )
                outcome.updatedSummary?.let { sessionRepo.updateSummaryCoverage(active, it, timeProvider.nowMillis()) }
                when (val p = outcome.prepared) {
                    is ChatOrchestrator.Prepared.Ready -> doStream(active, p.messages, apiKey)
                    is ChatOrchestrator.Prepared.OverHard -> {
                        val full = orchestrator.prepareFullRaw(currentSystemPrompt(), memory.content, messages, Config.IMAGE_PROMPT)
                        doStream(active, full.first, apiKey)
                    }
                    is ChatOrchestrator.Prepared.OverSafe ->
                        _state.update { it.copy(error = "上下文过长，请删除部分消息或新开会话") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = friendlyMessage(e)) }
            }
        }
    }

    fun deleteSingle(messageId: String) {
        val active = state.value.activeSessionId
        if (active.isBlank()) return
        val messages = sessionRepo.loadMessages(active)
        val removed = messages.firstOrNull { it.id == messageId } ?: return
        val updated = messages.filter { it.id != messageId }
        removed.imagePath?.let { runCatching { File(it).delete() } }
        sessionRepo.rewriteMessages(active, updated)
        val index = sessionRepo.loadIndex()
        val preview = updated.lastOrNull()?.let { if (it.recalled) "撤回了一条消息" else it.content.take(40) }.orEmpty()
        sessionRepo.saveIndex(
            index.copy(sessions = index.sessions.map {
                if (it.id == active) it.copy(lastMessagePreview = preview, messageCount = updated.size) else it
            }),
        )
        refreshAll()
    }

    fun pinToMemory(messageId: String) {
        val active = state.value.activeSessionId
        if (active.isBlank()) return
        val msg = sessionRepo.loadMessages(active).firstOrNull { it.id == messageId } ?: return
        if (msg.recalled || msg.content.isBlank() || msg.content == "[图片]") return
        val now = timeProvider.nowMillis()
        val current = memoryRepo.load()
        val line = "· " + (if (msg.role == "user") "用户" else "我") + "说过：「" + msg.content.take(120) + "」"
        val newContent = if (current.content.isBlank()) line else current.content.trimEnd() + "\n" + line
        memoryRepo.saveContent(newContent, now)
        refreshAll()
    }

    // ---------------- send ----------------

    fun sendMessage(raw: String) {
        val input = raw.trim()
        if (input.isEmpty() || _state.value.isStreaming) return
        val active = _state.value.activeSessionId
        if (active.isBlank()) return
        val apiKey = KeystoreHelper.loadApiKey(appContext)
        if (apiKey.isNullOrBlank()) {
            _state.update { it.copy(error = "请先在设置页填写 DeepSeek API Key") }
            return
        }

        val quote = _state.value.quoteMessage
        pendingInput = input
        pendingQuote = quote
        _state.update { it.copy(input = "", error = null, quoteMessage = null) }

        val memory = memoryRepo.load()
        val summary = sessionRepo.loadSummary(active)
        val messages = sessionRepo.loadMessages(active)
        val systemPrompt = currentSystemPrompt()

        viewModelScope.launch {
            try {
                val outcome = orchestrator.prepare(
                    systemPrompt, memory.content, summary, messages, input, apiKey,
                )
                outcome.updatedSummary?.let { new ->
                    sessionRepo.updateSummaryCoverage(active, new, timeProvider.nowMillis())
                }
                when (val p = outcome.prepared) {
                    is ChatOrchestrator.Prepared.Ready -> {
                        persistUserMessage(active, input, quote)
                        pendingStreamMessages = p.messages
                        doStream(active, p.messages, apiKey)
                    }
                    is ChatOrchestrator.Prepared.OverHard -> {
                        val fullRaw = orchestrator.prepareFullRaw(
                            systemPrompt, memory.content, messages, input,
                        )
                        pendingStreamMessages = fullRaw.first
                        _state.update {
                            it.copy(
                                input = pendingInput.orEmpty(),
                                showHardConfirm = true,
                                hardConfirmTokens = p.estimatedTokens,
                                fullRawTokens = p.fullRawTokens,
                            )
                        }
                    }
                    is ChatOrchestrator.Prepared.OverSafe -> {
                        _state.update { it.copy(error = "原文过长，只能带摘要和最近消息") }
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isStreaming = false, error = friendlyMessage(e)) }
            }
        }
    }

    fun confirmHardContinue() {
        val active = _state.value.activeSessionId
        val input = pendingInput
        val msgs = pendingStreamMessages
        val quote = pendingQuote
        _state.update { it.copy(showHardConfirm = false) }
        if (active.isBlank() || input.isNullOrEmpty() || msgs == null) return
        val apiKey = KeystoreHelper.loadApiKey(appContext) ?: return
        viewModelScope.launch {
            try {
                persistUserMessage(active, input, quote)
                doStream(active, msgs, apiKey)
            } catch (e: Exception) {
                _state.update { it.copy(isStreaming = false, error = friendlyMessage(e)) }
            }
        }
    }

    fun cancelHardContinue() {
        val saved = pendingInput
        pendingInput = null
        pendingStreamMessages = null
        pendingQuote = null
        _state.update { it.copy(showHardConfirm = false, input = saved.orEmpty()) }
    }

    fun stopStreaming() {
        streamJob?.cancel()
        streamJob = null
    }

    fun retryLast() {
        val active = _state.value.activeSessionId
        val msgs = pendingStreamMessages ?: return
        if (active.isBlank()) return
        val apiKey = KeystoreHelper.loadApiKey(appContext) ?: return
        doStream(active, msgs, apiKey)
    }

    private fun currentSystemPrompt(): String =
        Prompts.SYSTEM_PROMPT + relationshipManager.systemBlock(relationship.closeness) + contextBlock()

    private fun dateNote(): String {
        val now = Instant.now().atZone(ZoneId.systemDefault())
        val week = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
        return "今天是" + now.year + "年" + now.monthValue + "月" + now.dayOfMonth + "日 " + week[now.dayOfWeek.value - 1]
    }

    private fun contextBlock(): String {
        val sb = StringBuilder("\n\n【今天的日期】\n").append(dateNote())
        if (weatherNote.isNotBlank()) {
            sb.append("\n【今天的天气】\n").append(weatherNote)
        } else {
            sb.append("\n【今天的天气】\n未知（不要编造）")
        }
        return sb.toString()
    }

    private fun refreshWeather() {
        val city = AppPrefs.city(appContext).trim()
        if (city.isEmpty()) {
            weatherNote = ""
            return
        }
        viewModelScope.launch {
            val info = WeatherClient().fetch(city)
            weatherNote = info?.text().orEmpty()
        }
    }

    // ---------------- internals ----------------

    private fun refreshAll() {
        val index = sessionRepo.loadIndex()
        val memory = memoryRepo.load()
        val active = index.activeSessionId
        val messages = if (active.isBlank()) emptyList() else sessionRepo.loadMessages(active)
        val summary: SummaryFile? = if (active.isBlank()) null else sessionRepo.loadSummary(active)
        relationship = store.readRelationship()
        val tokens = estimateCurrentTokens(memory, summary, messages)
        _state.value = _state.value.copy(
            messages = messages,
            sessions = index.sessions,
            activeSessionId = active,
            sessionTitle = index.sessions.firstOrNull { it.id == active }?.title.orEmpty(),
            memoryContent = memory.content,
            hasApiKey = KeystoreHelper.hasApiKey(appContext),
            tokenEstimate = tokens,
            proactiveCountToday = index.proactive.sentCount,
            relationshipCloseness = relationship.closeness,
            relationshipStage = relationshipManager.stage(relationship.closeness),
            avatarPath = store.avatarFile.takeIf { it.exists() }?.absolutePath.orEmpty(),
            myAvatarPath = store.myAvatarFile.takeIf { it.exists() }?.absolutePath.orEmpty(),
            city = AppPrefs.city(appContext),
        )
    }

    private fun estimateCurrentTokens(memory: MemoryFile, summary: SummaryFile?, messages: List<Message>): Int =
        contextBuilder.buildNormal(currentSystemPrompt(), memory.content, summary, messages, "").estimatedTokens

    private fun persistUserMessage(sessionId: String, input: String, quote: Message?) {
        val msg = Message(
            id = newId(),
            role = "user",
            content = input,
            createdAt = timeProvider.nowMillis(),
            tokenEstimate = TokenEstimator.estimate(input),
            kind = "chat",
            status = "complete",
            quoteId = quote?.id,
            quoteText = quote?.content?.take(80),
            quoteRole = quote?.role,
        )
        sessionRepo.appendAndUpdateIndex(sessionId, msg, timeProvider.nowMillis())
        _state.update { it.copy(messages = sessionRepo.loadMessages(sessionId), sessions = sessionRepo.loadIndex().sessions) }
    }

    private fun doStream(sessionId: String, messages: List<ApiMessage>, apiKey: String) {
        _state.update { it.copy(isStreaming = true, streamingText = "", error = null) }
        streamJob = viewModelScope.launch {
            var accum = ""
            try {
                val result = orchestrator.streamWithPacing(messages, apiKey) { text ->
                    accum = text
                    _state.update { it.copy(streamingText = accum) }
                }
                finishStreaming(sessionId, result.content.ifEmpty { accum }, result.usage, "complete")
            } catch (ce: CancellationException) {
                finishStreaming(sessionId, accum, Usage(), "stopped")
                throw ce
            } catch (e: Exception) {
                val status = if (accum.isEmpty()) "error" else "stopped"
                finishStreaming(sessionId, accum, Usage(), status)
                if (accum.isEmpty()) {
                    _state.update { it.copy(isStreaming = false, error = friendlyMessage(e)) }
                }
            }
        }
    }

    private fun finishStreaming(sessionId: String, content: String, usage: Usage, status: String) {
        streamJob = null
        val now = timeProvider.nowMillis()
        if (content.isNotEmpty() && status != "error") {
            val msg = Message(
                id = newId(),
                role = "assistant",
                content = content,
                createdAt = now,
                tokenEstimate = TokenEstimator.estimate(content),
                kind = "chat",
                status = status,
                usage = usage,
            )
            sessionRepo.appendAndUpdateIndex(sessionId, msg, now)
            if (status == "complete") {
                relationship = relationshipManager.advance(relationship, now, timeProvider.today())
                store.writeRelationship(relationship)
            }
        }
        val messages = sessionRepo.loadMessages(sessionId)
        val summary = sessionRepo.loadSummary(sessionId)
        val tokens = estimateCurrentTokens(memoryRepo.load(), summary, messages)
        _state.update {
            it.copy(
                messages = messages,
                isStreaming = false,
                streamingText = "",
                tokenEstimate = tokens,
                sessions = sessionRepo.loadIndex().sessions,
                relationshipCloseness = relationship.closeness,
                relationshipStage = relationshipManager.stage(relationship.closeness),
            )
        }
    }

    private fun maybeProactive(index: com.example.liuruyi.data.IndexFile) {
        val active = index.activeSessionId
        if (active.isBlank() || _state.value.isStreaming) return
        if (!KeystoreHelper.hasApiKey(appContext)) return
        val messages = sessionRepo.loadMessages(active)
        if (!proactiveManager.shouldTrigger(index.proactive, messages)) return

        viewModelScope.launch {
            try {
                val memory = memoryRepo.load()
                val systemPrompt = currentSystemPrompt() +
                    (if (memory.content.isNotBlank()) "\n\n【全局长期记忆】\n" + memory.content else "")
                val text = proactiveManager.generate(
                    systemPrompt, messages, timeProvider.nowText(),
                    KeystoreHelper.loadApiKey(appContext).orEmpty(),
                )
                when {
                    text.isNullOrEmpty() -> markProactiveAttempted(active)
                    else -> {
                        val msg = Message(
                            id = newId(),
                            role = "assistant",
                            content = text,
                            createdAt = timeProvider.nowMillis(),
                            tokenEstimate = TokenEstimator.estimate(text),
                            kind = "proactive",
                            status = "complete",
                        )
                        sessionRepo.appendAndUpdateIndex(active, msg, timeProvider.nowMillis())
                        val updated = sessionRepo.loadIndex()
                        val newProactive = updated.proactive.copy(
                            sentCount = updated.proactive.sentCount + 1,
                            lastOpenSent = true,
                        )
                        sessionRepo.saveIndex(updated.copy(proactive = newProactive))
                        refreshAll()
                    }
                }
            } catch (e: Exception) {
                markProactiveAttempted(active)
            }
        }
    }

    private fun markProactiveAttempted(active: String) {
        val index = sessionRepo.loadIndex()
        val updated = index.copy(proactive = index.proactive.copy(lastOpenSent = true))
        sessionRepo.saveIndex(updated)
        _state.update { it.copy(proactiveCountToday = updated.proactive.sentCount) }
    }

    private fun friendlyMessage(e: Throwable): String = when (e) {
        is DeepSeekException -> when (e.statusCode) {
            401 -> "API Key 无效，请在设置页检查"
            402 -> "余额不足，请检查账户"
            429 -> "请求过于频繁，稍后再试"
            else -> e.message ?: "请求失败"
        }
        is IOException -> "网络连接失败，请检查网络"
        else -> e.message ?: "发生未知错误"
    }
}
