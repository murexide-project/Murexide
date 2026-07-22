package com.juhao.murexide.ui.mine

import android.widget.Toast
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.juhao.murexide.ui.components.StyledIconButton
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    token: String,
    onBackClick: () -> Unit,
    onProfileSaved: () -> Unit,
    viewModel: MineViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            val application = LocalContext.current.applicationContext

            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return MineViewModel(application as android.app.Application, token) as T
            }
        }
    )
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollState = rememberScrollState()

    var nickname by rememberSaveable { mutableStateOf("") }
    var introduction by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableIntStateOf(3) }
    var birthday by rememberSaveable { mutableLongStateOf(0L) }
    var province by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf("") }
    var district by rememberSaveable { mutableStateOf("") }
    var locationCode by rememberSaveable { mutableStateOf("") }
    var showBirthdayPicker by rememberSaveable { mutableStateOf(false) }
    var initializedUserId by rememberSaveable { mutableStateOf<String?>(null) }
    var initializedProfileVersion by rememberSaveable { mutableLongStateOf(Long.MIN_VALUE) }

    LaunchedEffect(uiState) {
        val state = uiState
        if (state is MineUiState.Success) {
            if (initializedUserId != state.userInfo.id) {
                nickname = state.userInfo.name
                introduction = state.introduction
                gender = 3
                birthday = 0L
                province = ""
                city = ""
                district = ""
                locationCode = ""
                initializedUserId = state.userInfo.id
                initializedProfileVersion = Long.MIN_VALUE
            }

            state.userProfile?.let { profile ->
                if (initializedProfileVersion != profile.update_time) {
                    introduction = state.introduction
                    gender = profile.gender.takeIf { it in 1..3 } ?: 3
                    birthday = profile.birthday
                    province = profile.province
                    city = profile.city
                    district = profile.district
                    locationCode = profile.locationCode
                    initializedProfileVersion = profile.update_time
                }
            }

            if (
                state.userProfile == null &&
                state.introduction.isNotEmpty() &&
                introduction.isEmpty()
            ) {
                introduction = state.introduction
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collectLatest { event ->
            when (event) {
                is MineViewModel.MineEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }

                is MineViewModel.MineEvent.ProfileUpdated -> {
                    onProfileSaved()
                }
            }
        }
    }

    val successState = uiState as? MineUiState.Success
    val isSaving = successState?.isSavingProfile == true
    val canSave = successState != null && !isSaving
    val saveChanges = {
        viewModel.saveProfile(
            nickname = nickname,
            introduction = introduction,
            gender = gender,
            birthday = birthday,
            province = province,
            city = city,
            district = district,
            locationCode = locationCode
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("编辑资料") },
                navigationIcon = {
                    StyledIconButton(onClick = onBackClick, enabled = !isSaving) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                scrollBehavior = scrollBehavior,
                actions = {
                    TextButton(
                        onClick = saveChanges,
                        enabled = canSave,
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isSaving) "保存中" else "保存")
                    }
                }
            )
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is MineUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is MineUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "资料加载失败",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        FilledTonalButton(onClick = viewModel::loadUserInfo) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("重新加载")
                        }
                    }
                }
            }

            is MineUiState.Success -> {
                EditProfileContent(
                    nickname = nickname,
                    onNicknameChange = { nickname = it },
                    introduction = introduction,
                    onIntroductionChange = { introduction = it },
                    gender = gender,
                    onGenderChange = { gender = it },
                    birthday = birthday,
                    onBirthdayClick = { showBirthdayPicker = true },
                    province = province,
                    onProvinceChange = { province = it },
                    city = city,
                    onCityChange = { city = it },
                    district = district,
                    onDistrictChange = { district = it },
                    locationCode = locationCode,
                    onLocationCodeChange = { locationCode = it },
                    enabled = !state.isSavingProfile,
                    scrollState = scrollState,
                    paddingValues = paddingValues
                )
            }
        }
    }

    if (showBirthdayPicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = birthday.toPickerMillis(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    return utcTimeMillis <= System.currentTimeMillis()
                }
            }
        )

        DatePickerDialog(
            onDismissRequest = { showBirthdayPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            birthday = it / 1000L
                        }
                        showBirthdayPicker = false
                    },
                    enabled = datePickerState.selectedDateMillis != null
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBirthdayPicker = false }) {
                    Text("取消")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun EditProfileContent(
    nickname: String,
    onNicknameChange: (String) -> Unit,
    introduction: String,
    onIntroductionChange: (String) -> Unit,
    gender: Int,
    onGenderChange: (Int) -> Unit,
    birthday: Long,
    onBirthdayClick: () -> Unit,
    province: String,
    onProvinceChange: (String) -> Unit,
    city: String,
    onCityChange: (String) -> Unit,
    district: String,
    onDistrictChange: (String) -> Unit,
    locationCode: String,
    onLocationCodeChange: (String) -> Unit,
    enabled: Boolean,
    scrollState: ScrollState,
    paddingValues: PaddingValues
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .imePadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "完善公开资料，让大家更好地认识你",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        ProfileSectionCard(
            icon = Icons.Rounded.Badge,
            title = "基本资料",
            description = "昵称、签名、生日与性别会展示在个人主页"
        ) {
            OutlinedTextField(
                value = nickname,
                onValueChange = onNicknameChange,
                label = { Text("昵称") },
                leadingIcon = {
                    Icon(Icons.Rounded.Person, contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                enabled = enabled,
                singleLine = true
            )

            OutlinedTextField(
                value = introduction,
                onValueChange = onIntroductionChange,
                label = { Text("个性签名") },
                leadingIcon = {
                    Icon(Icons.Rounded.FormatQuote, contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                enabled = enabled,
                minLines = 3,
                maxLines = 5
            )

            ProfilePickerField(
                icon = Icons.Rounded.Cake,
                label = "生日",
                value = birthday.formatBirthday(),
                onClick = onBirthdayClick,
                enabled = enabled
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "性别",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(1 to "男", 2 to "女", 3 to "其他").forEach { (value, label) ->
                        val selected = gender == value
                        FilterChip(
                            selected = selected,
                            onClick = { onGenderChange(value) },
                            label = {
                                Text(
                                    text = label,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            },
                            leadingIcon = if (selected) {
                                {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                                    )
                                }
                            } else {
                                null
                            },
                            modifier = Modifier.weight(1f),
                            enabled = enabled
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        ProfileSectionCard(
            icon = Icons.Rounded.LocationOn,
            title = "所在地",
            description = "按行政区划填写省、市、区县和地区代码"
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProfileTextField(
                    value = province,
                    onValueChange = onProvinceChange,
                    label = "省份",
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                )
                ProfileTextField(
                    value = city,
                    onValueChange = onCityChange,
                    label = "城市",
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProfileTextField(
                    value = district,
                    onValueChange = onDistrictChange,
                    label = "区县",
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                )
                ProfileTextField(
                    value = locationCode,
                    onValueChange = {
                        onLocationCodeChange(it.filter { char -> char.isDigit() }.take(6))
                    },
                    label = "地区代码",
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = "地区代码为 6 位行政区划码，例如东城区为 110101。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ProfileSectionCard(
    icon: ImageVector,
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            content()
        }
    }
}

@Composable
private fun ProfilePickerField(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit,
    enabled: Boolean
) {
    OutlinedCard(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = Icons.Rounded.CalendarMonth,
                contentDescription = "选择生日",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        enabled = enabled,
        keyboardOptions = keyboardOptions,
        singleLine = true
    )
}

private fun Long.toPickerMillis(): Long? {
    return takeIf { it in 1L..253_402_300_799L }?.times(1000L)
}

private fun Long.formatBirthday(): String {
    val millis = toPickerMillis() ?: return "请选择生日"
    return runCatching {
        SimpleDateFormat("yyyy年M月d日", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(millis))
    }.getOrDefault("请选择生日")
}
