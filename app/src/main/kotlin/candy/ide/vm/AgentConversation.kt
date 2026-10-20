package candy.ide.vm

/** 一段往期 agent 对话的快照（落盘到 files/candy/agent/conversations.json） */
data class AgentConversation(
    val time: Long,
    var title: String,
    val messages: List<ChatMessage>,
    /** 置顶：抽屉里排在最前 */
    var pinned: Boolean = false,
)
