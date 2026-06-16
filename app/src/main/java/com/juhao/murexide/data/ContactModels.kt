package com.juhao.murexide.data

data class ContactItem(
    val chatId: String,
    val chatType: Int,
    val remark: String?,
    val avatarUrl: String,
    val permissionLevel: Int,
    val noDisturb: Boolean,
    val name: String
)

data class ContactGroup(
    val groupName: String,
    val chatType: Int,
    val contacts: List<ContactItem>
)
