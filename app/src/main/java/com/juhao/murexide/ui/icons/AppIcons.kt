package com.juhao.murexide.ui.icons

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*
import com.composables.icons.materialsymbols.roundedfilled.*

object AppIcons {
    val AccessTime get() = MaterialSymbols.Rounded.Schedule
    val Add get() = MaterialSymbols.Rounded.Add
    val AdminPanelSettings get() = MaterialSymbols.Rounded.Admin_panel_settings
    val Android get() = MaterialSymbols.Rounded.Android
    val Animation get() = MaterialSymbols.Rounded.Animation
    val ArrowBack get() = MaterialSymbols.Rounded.Arrow_back
    val ArrowBackIosNew get() = MaterialSymbols.Rounded.Arrow_back_ios_new
    val Article get() = MaterialSymbols.Rounded.Article
    val AttachFile get() = MaterialSymbols.Rounded.Attach_file
    val AudioFile get() = MaterialSymbols.Rounded.Audio_file
    val Badge get() = MaterialSymbols.Rounded.Badge
    val Book get() = MaterialSymbols.Rounded.Book
    val Bookmark get() = MaterialSymbols.RoundedFilled.Bookmark
    val BookmarkBorder get() = MaterialSymbols.Rounded.Bookmark
    val Boy get() = MaterialSymbols.Rounded.Boy
    val Cake get() = MaterialSymbols.Rounded.Cake
    val CalendarMonth get() = MaterialSymbols.Rounded.Calendar_month
    val CardGiftcard get() = MaterialSymbols.Rounded.Redeem
    val Category get() = MaterialSymbols.Rounded.Category
    val ChatBubble get() = MaterialSymbols.Rounded.Chat_bubble
    val ChatBubbleOutline get() = MaterialSymbols.Rounded.Chat_bubble
    val Check get() = MaterialSymbols.Rounded.Check
    val CheckBox get() = MaterialSymbols.RoundedFilled.Check_box
    val CheckBoxOutlineBlank get() = MaterialSymbols.Rounded.Check_box_outline_blank
    val CheckCircle get() = MaterialSymbols.RoundedFilled.Check_circle
    val ChevronRight get() = MaterialSymbols.Rounded.Chevron_right
    val Close get() = MaterialSymbols.Rounded.Close
    val Code get() = MaterialSymbols.Rounded.Code
    val Contacts get() = MaterialSymbols.Rounded.Contacts
    val ContentCopy get() = MaterialSymbols.Rounded.Content_copy
    val Colorize get() = MaterialSymbols.Rounded.Colorize
    val Delete get() = MaterialSymbols.Rounded.Delete
    val Description get() = MaterialSymbols.Rounded.Description
    val Download get() = MaterialSymbols.Rounded.Download
    val Draw get() = MaterialSymbols.Rounded.Draw
    val DriveFileRenameOutline get() = MaterialSymbols.Rounded.Drive_file_rename_outline
    val Edit get() = MaterialSymbols.Rounded.Edit
    val Email get() = MaterialSymbols.Rounded.Mail
    val Event get() = MaterialSymbols.Rounded.Event
    val Explore get() = MaterialSymbols.Rounded.Explore
    val Face get() = MaterialSymbols.Rounded.Face
    val Favorite get() = MaterialSymbols.RoundedFilled.Favorite
    val FavoriteBorder get() = MaterialSymbols.Rounded.Favorite
    val FolderZip get() = MaterialSymbols.Rounded.Folder_zip
    val FormatQuote get() = MaterialSymbols.Rounded.Format_quote
    val Girl get() = MaterialSymbols.Rounded.Girl
    val Group get() = MaterialSymbols.Rounded.Group
    val Groups get() = MaterialSymbols.Rounded.Groups
    val History get() = MaterialSymbols.Rounded.History
    val Help get() = MaterialSymbols.Rounded.Help
    val HowToReg get() = MaterialSymbols.Rounded.How_to_reg
    val Image get() = MaterialSymbols.Rounded.Image
    val ImageNotSupported get() = MaterialSymbols.Rounded.Image_not_supported
    val Inbox get() = MaterialSymbols.Rounded.Inbox
    val Info get() = MaterialSymbols.Rounded.Info
    val InsertDriveFile get() = MaterialSymbols.Rounded.Draft
    val Keep get() = MaterialSymbols.Rounded.Keep
    val Key get() = MaterialSymbols.Rounded.Key
    val Keyboard get() = MaterialSymbols.Rounded.Keyboard
    val KeyboardArrowDown get() = MaterialSymbols.Rounded.Keyboard_arrow_down
    val KeyboardArrowUp get() = MaterialSymbols.Rounded.Keyboard_arrow_up
    val LaptopChromebook get() = MaterialSymbols.Rounded.Laptop_chromebook
    val Link get() = MaterialSymbols.Rounded.Link
    val List get() = MaterialSymbols.Rounded.List
    val LocalFireDepartment get() = MaterialSymbols.Rounded.Local_fire_department
    val LocationOn get() = MaterialSymbols.Rounded.Location_on
    val Lock get() = MaterialSymbols.Rounded.Lock
    val Logout get() = MaterialSymbols.Rounded.Logout
    val MicOff get() = MaterialSymbols.Rounded.Mic_off
    val MonetizationOn get() = MaterialSymbols.Rounded.Monetization_on
    val Mood get() = MaterialSymbols.Rounded.Mood
    val MoreVert get() = MaterialSymbols.Rounded.More_vert
    val Movie get() = MaterialSymbols.Rounded.Movie
    val NavigateNext get() = MaterialSymbols.Rounded.Navigate_next
    val Notifications get() = MaterialSymbols.Rounded.Notifications
    val NotificationsOff get() = MaterialSymbols.Rounded.Notifications_off
    val Opacity get() = MaterialSymbols.Rounded.Opacity
    val People get() = MaterialSymbols.Rounded.Group
    val Person get() = MaterialSymbols.Rounded.Person
    val PersonAdd get() = MaterialSymbols.Rounded.Person_add
    val PersonOutline get() = MaterialSymbols.Rounded.Person
    val PersonRemove get() = MaterialSymbols.Rounded.Person_remove
    val Phone get() = MaterialSymbols.Rounded.Call
    val PictureAsPdf get() = MaterialSymbols.Rounded.Picture_as_pdf
    val PlayArrow get() = MaterialSymbols.Rounded.Play_arrow
    val PlayCircle get() = MaterialSymbols.Rounded.Play_circle
    val PowerSettingsNew get() = MaterialSymbols.Rounded.Power_settings_new
    val Redo get() = MaterialSymbols.Rounded.Redo
    val Refresh get() = MaterialSymbols.Rounded.Refresh
    val RoundedCorner get() = MaterialSymbols.Rounded.Rounded_corner
    val SaveAlt get() = MaterialSymbols.Rounded.Download
    val Screenshot get() = MaterialSymbols.Rounded.Screenshot
    val Search get() = MaterialSymbols.Rounded.Search
    val Send get() = MaterialSymbols.Rounded.Send
    val Settings get() = MaterialSymbols.Rounded.Settings
    val Share get() = MaterialSymbols.Rounded.Share
    val Slideshow get() = MaterialSymbols.Rounded.Slideshow
    val SmartToy get() = MaterialSymbols.Rounded.Smart_toy
    val SwitchAccount get() = MaterialSymbols.Rounded.Switch_account
    val TableChart get() = MaterialSymbols.Rounded.Table_chart
    val Tag get() = MaterialSymbols.Rounded.Tag
    val TextFields get() = MaterialSymbols.Rounded.Text_fields
    val Undo get() = MaterialSymbols.Rounded.Undo
    val Update get() = MaterialSymbols.Rounded.Update
    val Verified get() = MaterialSymbols.Rounded.Verified
    val VideoFile get() = MaterialSymbols.Rounded.Video_file
    val Visibility get() = MaterialSymbols.Rounded.Visibility
    val VisibilityOff get() = MaterialSymbols.Rounded.Visibility_off
    val VolumeUp get() = MaterialSymbols.Rounded.Volume_up
    val Warning get() = MaterialSymbols.Rounded.Warning
    val WbSunny get() = MaterialSymbols.Rounded.Wb_sunny
    val WorkspacePremium get() = MaterialSymbols.Rounded.Workspace_premium
}

object AppFilledIcons {
    val ChatBubble get() = MaterialSymbols.RoundedFilled.Chat_bubble
    val Contacts get() = MaterialSymbols.RoundedFilled.Contacts
    val Group get() = MaterialSymbols.RoundedFilled.Group
    val Explore get() = MaterialSymbols.RoundedFilled.Explore
    val Person get() = MaterialSymbols.RoundedFilled.Person
}

@Composable
fun AutoMirroredIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = modifier.graphicsLayer { scaleX = if (isRtl) -1f else 1f },
        tint = tint,
    )
}
