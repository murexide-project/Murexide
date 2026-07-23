package com.juhao.murexide.ui.contact

import com.juhao.murexide.data.ContactItem
import java.nio.charset.Charset
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

internal data class ContactSection(
    val initial: Char,
    val contacts: List<ContactItem>
)

internal val ContactIndexLetters: List<Char> = ('A'..'Z').toList() + '#'

internal fun contactDisplayName(contact: ContactItem): String =
    contact.remark?.takeIf { it.isNotBlank() }
        ?: contact.name.takeIf { it.isNotBlank() }
        ?: contact.chatId

internal fun buildContactSections(contacts: List<ContactItem>): List<ContactSection> {
    val collator = Collator.getInstance(Locale.CHINA)
    val nameComparator = Comparator<ContactItem> { left, right ->
        val byName = collator.compare(contactDisplayName(left), contactDisplayName(right))
        if (byName != 0) byName else left.chatId.compareTo(right.chatId)
    }

    return contacts
        .distinctBy { "${it.chatType}:${it.chatId}" }
        .groupBy { contactInitial(contactDisplayName(it)) }
        .entries
        .sortedBy { initialOrder(it.key) }
        .map { (initial, sectionContacts) ->
            ContactSection(
                initial = initial,
                contacts = sectionContacts.sortedWith(nameComparator)
            )
        }
}

internal fun contactInitial(name: String): Char {
    val first = name.trim().firstOrNull() ?: return '#'
    val upper = first.uppercaseChar()
    if (upper in 'A'..'Z') return upper

    val normalized = Normalizer.normalize(first.toString(), Normalizer.Form.NFD)
    normalized.firstOrNull { it.uppercaseChar() in 'A'..'Z' }
        ?.let { return it.uppercaseChar() }

    return chinesePinyinInitial(first) ?: '#'
}

internal fun closestAvailableInitial(
    requested: Char,
    available: Collection<Char>
): Char? {
    if (available.isEmpty()) return null
    if (requested in available) return requested
    if (requested == '#') return available.lastOrNull()

    val requestedOrder = initialOrder(requested)
    return available
        .filter { it != '#' }
        .sortedBy(::initialOrder)
        .firstOrNull { initialOrder(it) >= requestedOrder }
        ?: available.filter { it != '#' }.maxByOrNull(::initialOrder)
        ?: available.first()
}

private fun initialOrder(initial: Char): Int =
    if (initial in 'A'..'Z') initial - 'A' else Int.MAX_VALUE

private fun chinesePinyinInitial(character: Char): Char? {
    val bytes = character.toString().toByteArray(Gb2312)
    if (bytes.size < 2) return null

    val code = (bytes[0].toInt() and 0xff) * 256 +
        (bytes[1].toInt() and 0xff) - 65_536
    for (index in PinyinBoundaries.indices.reversed()) {
        if (code >= PinyinBoundaries[index]) return PinyinInitials[index]
    }
    return null
}

private val Gb2312: Charset = Charset.forName("GB2312")

private val PinyinBoundaries = intArrayOf(
    -20319, -20284, -19776, -19219, -18711, -18527, -18240,
    -17923, -17418, -16475, -16213, -15641, -15166, -14923,
    -14915, -14631, -14150, -14091, -13319, -12839, -12557,
    -11848, -11056
)

private val PinyinInitials = charArrayOf(
    'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'J', 'K', 'L',
    'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'W', 'X', 'Y', 'Z'
)
