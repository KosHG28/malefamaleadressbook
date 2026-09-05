package com.koshg.interlude.ui

import android.app.DatePickerDialog
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.koshg.interlude.R
import com.koshg.interlude.data.DeclineReason
import com.koshg.interlude.data.Initiator
import com.koshg.interlude.data.MasturbationEntry
import com.koshg.interlude.data.PeriodEntry
import com.koshg.interlude.data.ProposalEntry
import com.koshg.interlude.data.SexEntry
import com.koshg.interlude.haptics.HapticEvent
import com.koshg.interlude.haptics.LocalHaptics
import com.koshg.interlude.ui.theme.appColors
import com.koshg.interlude.util.fullDateLabel
import com.koshg.interlude.util.toLocalDateOrNull
import java.time.LocalDate

enum class AddType(@StringRes val labelRes: Int) {
    Period(R.string.type_period),
    Sex(R.string.type_sex),
    Proposal(R.string.type_proposal),
    Masturbation(R.string.type_masturbation)
}

private fun AddType.icon(): ImageVector = when (this) {
    AddType.Period -> Icons.Default.WaterDrop
    AddType.Sex -> Icons.Default.Favorite
    AddType.Proposal -> Icons.Default.FavoriteBorder
    AddType.Masturbation -> Icons.Default.SelfImprovement
}

@Composable
private fun AddType.chipColor(): Color {
    val colors = appColors()
    return when (this) {
        AddType.Period -> colors.menstrual
        AddType.Sex -> colors.intimacy
        AddType.Proposal -> colors.proposalAccepted
        AddType.Masturbation -> colors.solo
    }
}

@Composable
private fun TypeChipRow(selected: AddType, onSelect: (AddType) -> Unit) {
    val haptics = LocalHaptics.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AddType.entries.forEach { type ->
            val color = type.chipColor()
            val isSelected = type == selected
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isSelected) color else color.copy(alpha = 0.15f))
                    .clickable {
                        haptics.perform(HapticEvent.Tap)
                        onSelect(type)
                    }
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Icon(
                    type.icon(),
                    contentDescription = null,
                    tint = if (isSelected) Color.White else color,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    stringResource(type.labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) Color.White else color
                )
            }
        }
    }
}

/** Rounded, accent-tinted text field colors shared by every sheet, in place of stock Material outlines. */
@Composable
internal fun sheetFieldColors(): TextFieldColors {
    val appColors = appColors()
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = appColors.accent,
        focusedLabelColor = appColors.accent,
        cursorColor = appColors.accent,
        unfocusedBorderColor = appColors.textSecondary.copy(alpha = 0.35f),
        unfocusedLabelColor = appColors.textSecondary,
        focusedTextColor = appColors.textPrimary,
        unfocusedTextColor = appColors.textPrimary
    )
}

internal val sheetFieldShape = RoundedCornerShape(14.dp)

/** Quick-pick decline reasons, stored as [DeclineReason] codes rather than as the words on the
 *  chip, so the fatigue pattern in [com.koshg.interlude.util.computeCorrelationInsights] survives
 *  both a rephrasing and a change of language. Free text stays available behind "Other" and is
 *  stored as typed. */
@Composable
private fun DeclineReasonSelector(reason: String, onReasonChange: (String) -> Unit) {
    val haptics = LocalHaptics.current
    val appColors = appColors()
    val presets = DeclineReason.entries
    var showCustomField by remember {
        mutableStateOf(reason.isNotBlank() && DeclineReason.fromStorage(reason) == null)
    }

    Column {
        Text(
            stringResource(R.string.field_decline_reason),
            style = MaterialTheme.typography.labelLarge,
            color = appColors.textSecondary
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // null stands for the "Other" chip, which opens the free-text field instead of
            // storing a code.
            (presets + null).forEach { preset ->
                val isSelected = if (preset == null) {
                    showCustomField
                } else {
                    !showCustomField && reason == preset.storageValue
                }
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isSelected) appColors.accent else appColors.accent.copy(alpha = 0.12f))
                        .clickable {
                            haptics.perform(HapticEvent.Tap)
                            if (preset == null) {
                                showCustomField = true
                                onReasonChange("")
                            } else {
                                showCustomField = false
                                onReasonChange(preset.storageValue)
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Text(
                        stringResource(preset?.labelRes ?: R.string.decline_reason_other),
                        color = if (isSelected) Color.White else appColors.accent,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
        if (showCustomField) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = reason,
                onValueChange = onReasonChange,
                label = { Text(stringResource(R.string.field_custom_reason)) },
                shape = sheetFieldShape,
                colors = sheetFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Applies a system background-blur to whatever sits behind this dialog's own window, layered
 * under the platform's default scrim dim -- the "frosted glass" look from Android 12+'s
 * [android.view.Window.setBackgroundBlurRadius], which silently no-ops on a device/GPU that
 * doesn't support it rather than crashing. Must run inside a [Dialog]'s content, where the
 * composition's [LocalView] parent is the dialog's own [DialogWindowProvider].
 */
@Composable
private fun DialogBackgroundBlur(radiusPx: Int = 90) {
    val view = LocalView.current
    LaunchedEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@LaunchedEffect
        // Background blur (unlike FLAG_BLUR_BEHIND + blurBehindRadius) only blurs the area
        // behind the window's own bounds -- exactly what's wanted here since the dialog's
        // window already spans the full screen (usePlatformDefaultWidth = false + a
        // fillMaxSize root Box), so no extra window flag is needed.
        window.setBackgroundBlurRadius(radiusPx)
    }
}

/**
 * Single dialog for creating a new entry of any kind — a row of colored type chips up top swaps
 * the fields below, so picking what to log and filling it in happens in one open/close instead
 * of a chooser sheet followed by a second, type-specific one.
 *
 * A centered, floating card rather than a full-width bottom sheet: rounded on every corner (not
 * just the top), with its own shadow and a blurred/dimmed scrim behind it, so it reads as a
 * compact popup instead of a shutter that slices the screen in half.
 */
@Composable
fun UnifiedAddSheet(
    initialType: AddType,
    initialDate: LocalDate,
    initialEndDate: LocalDate? = null,
    fabOrigin: Offset = Offset.Unspecified,
    onDismiss: () -> Unit,
    onSavePeriod: (PeriodEntry) -> Unit,
    onSaveSex: (SexEntry) -> Unit,
    onSaveProposal: (ProposalEntry) -> Unit,
    onSaveMasturbation: (MasturbationEntry) -> Unit
) {
    val appColors = appColors()

    var type by remember { mutableStateOf(initialType) }
    var date by remember { mutableStateOf(initialDate) }
    var periodEndDate by remember { mutableStateOf(initialEndDate) }
    var notes by remember { mutableStateOf("") }

    var initiator by remember { mutableStateOf(Initiator.ME) }
    // Sex keeps a count per person; masturbation needs only one, since its own person selector
    // already says whose it is.
    var myOrgasmCount by remember { mutableStateOf(0) }
    var partnerOrgasmCount by remember { mutableStateOf(0) }
    var orgasmCount by remember { mutableStateOf(0) }
    var proposalAnswer by remember { mutableStateOf(ProposalAnswer.ACCEPTED) }
    var declineReason by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DialogBackgroundBlur()
        val maxCardHeight = LocalConfiguration.current.screenHeightDp.dp * 0.85f

        // The card doesn't just fade in centered -- it grows out from wherever the FAB sits on
        // screen, echoing the "+" morphing into the form itself. The card's own final bounds
        // aren't known until after layout, so this approximates its landing center as the
        // screen's center (true for this fillMaxSize, center-aligned Box) rather than chasing
        // exact coordinates -- plenty convincing for a launch animation, far more robust than a
        // real shared-element transform.
        val density = LocalDensity.current
        val configuration = LocalConfiguration.current
        val morphOrigin = if (fabOrigin.isSpecified) {
            val screenCenterPx = with(density) {
                Offset(
                    x = (configuration.screenWidthDp.dp / 2).toPx(),
                    y = (configuration.screenHeightDp.dp / 2).toPx()
                )
            }
            fabOrigin - screenCenterPx
        } else {
            Offset.Zero
        }
        val morphProgress = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            morphProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxCardHeight)
                    .graphicsLayer {
                        val t = morphProgress.value
                        val scale = 0.15f + 0.85f * t
                        scaleX = scale
                        scaleY = scale
                        alpha = t.coerceIn(0f, 1f)
                        translationX = morphOrigin.x * (1f - t)
                        translationY = morphOrigin.y * (1f - t)
                    }
                    .shadow(elevation = 24.dp, shape = RoundedCornerShape(28.dp))
                    .clip(RoundedCornerShape(28.dp))
                    .background(appColors.warmSurface)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(stringResource(R.string.sheet_new_entry), style = MaterialTheme.typography.titleLarge, color = appColors.textPrimary)

                TypeChipRow(selected = type) { type = it }

                DateField(date) { date = it }

                when (type) {
                    AddType.Period -> PeriodEndDateField(startDate = date, endDate = periodEndDate) { periodEndDate = it }

                    AddType.Sex -> {
                        InitiatorSelector(initiator) { initiator = it }
                        CountStepper(stringResource(R.string.field_my_orgasms), myOrgasmCount) { myOrgasmCount = it }
                        CountStepper(stringResource(R.string.field_partner_orgasms), partnerOrgasmCount) { partnerOrgasmCount = it }
                    }

                    AddType.Proposal -> {
                        InitiatorSelector(initiator) { initiator = it }
                        ProposalAnswerSelector(proposalAnswer) { proposalAnswer = it }
                        if (proposalAnswer == ProposalAnswer.DECLINED) {
                            DeclineReasonSelector(declineReason) { declineReason = it }
                        }
                    }

                    AddType.Masturbation -> {
                        InitiatorSelector(initiator) { initiator = it }
                        CountStepper(stringResource(R.string.field_orgasm_count), orgasmCount) { orgasmCount = it }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.field_note)) },
                    minLines = 2,
                    maxLines = 4,
                    shape = sheetFieldShape,
                    colors = sheetFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                SheetActions(
                    onDismiss = onDismiss,
                    onDelete = null,
                    onSave = {
                        when (type) {
                            AddType.Period -> onSavePeriod(
                                PeriodEntry(
                                    id = newEventId(),
                                    startDate = date.toString(),
                                    endDate = periodEndDate?.takeIf { !it.isBefore(date) }?.toString(),
                                    notes = notes.trim()
                                )
                            )

                            AddType.Sex -> onSaveSex(
                                SexEntry(
                                    id = newEventId(),
                                    date = date.toString(),
                                    initiator = initiator.storageValue,
                                    myOrgasmCount = myOrgasmCount,
                                    partnerOrgasmCount = partnerOrgasmCount,
                                    notes = notes.trim()
                                )
                            )

                            AddType.Proposal -> onSaveProposal(
                                ProposalEntry(
                                    id = newEventId(),
                                    date = date.toString(),
                                    initiator = initiator.storageValue,
                                    accepted = proposalAnswer == ProposalAnswer.ACCEPTED,
                                    answered = proposalAnswer != ProposalAnswer.PENDING,
                                    declineReason = if (proposalAnswer == ProposalAnswer.DECLINED) declineReason.trim() else "",
                                    notes = notes.trim()
                                )
                            )

                            AddType.Masturbation -> onSaveMasturbation(
                                MasturbationEntry(
                                    id = newEventId(),
                                    date = date.toString(),
                                    person = initiator.storageValue,
                                    orgasmCount = orgasmCount,
                                    notes = notes.trim()
                                )
                            )
                        }
                    }
                )
            }
        }
    }
}

/** A rounded, tappable pill showing the picked date — replaces a stock read-only text field. */
@Composable
private fun DateField(date: LocalDate, onDateChange: (LocalDate) -> Unit) {
    val context = LocalContext.current
    val appColors = appColors()
    val haptics = LocalHaptics.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(sheetFieldShape)
            .background(appColors.warmBackground)
            .border(1.dp, appColors.accent.copy(alpha = 0.3f), sheetFieldShape)
            .clickable {
                haptics.perform(HapticEvent.Tap)
                DatePickerDialog(
                    context,
                    { _, y, m, d -> onDateChange(LocalDate.of(y, m + 1, d)) },
                    date.year, date.monthValue - 1, date.dayOfMonth
                ).show()
            }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Icon(Icons.Default.DateRange, contentDescription = null, tint = appColors.accent, modifier = Modifier.size(20.dp))
        Text(
            fullDateLabel(date),
            style = MaterialTheme.typography.bodyLarge,
            color = appColors.textPrimary,
            modifier = Modifier.weight(1f)
        )
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = appColors.textSecondary, modifier = Modifier.size(18.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InitiatorSelector(selected: Initiator, onSelect: (Initiator) -> Unit) {
    val haptics = LocalHaptics.current
    val appColors = appColors()
    Column {
        Text(stringResource(R.string.field_initiator), style = MaterialTheme.typography.labelLarge, color = appColors.textSecondary)
        Spacer(Modifier.height(6.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            Initiator.entries.forEachIndexed { index, entry ->
                val isSelected = selected == entry
                SegmentedButton(
                    selected = isSelected,
                    onClick = {
                        haptics.perform(HapticEvent.Tap)
                        onSelect(entry)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = Initiator.entries.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = appColors.accent,
                        activeContentColor = Color.White,
                        activeBorderColor = appColors.accent,
                        inactiveContainerColor = Color.Transparent,
                        inactiveContentColor = appColors.textPrimary,
                        inactiveBorderColor = appColors.textSecondary.copy(alpha = 0.35f)
                    )
                ) { Text(stringResource(entry.labelRes), color = if (isSelected) Color.White else appColors.textPrimary) }
            }
        }
    }
}

/** UI-only grouping of [ProposalEntry.accepted]/[ProposalEntry.answered] into one three-way
 *  choice -- storage keeps the two separate booleans (so a pre-existing, always-answered
 *  proposal from before this state existed still reads correctly), the sheets only ever show
 *  and edit this enum. */
private enum class ProposalAnswer(@StringRes val labelRes: Int) {
    ACCEPTED(R.string.answer_accepted),
    DECLINED(R.string.answer_declined),
    PENDING(R.string.answer_pending)
}

private fun proposalAnswerOf(accepted: Boolean, answered: Boolean): ProposalAnswer = when {
    !answered -> ProposalAnswer.PENDING
    accepted -> ProposalAnswer.ACCEPTED
    else -> ProposalAnswer.DECLINED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProposalAnswerSelector(selected: ProposalAnswer, onSelect: (ProposalAnswer) -> Unit) {
    val haptics = LocalHaptics.current
    val appColors = appColors()
    Column {
        Text(stringResource(R.string.field_answer), style = MaterialTheme.typography.labelLarge, color = appColors.textSecondary)
        Spacer(Modifier.height(6.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ProposalAnswer.entries.forEachIndexed { index, entry ->
                val isSelected = selected == entry
                SegmentedButton(
                    selected = isSelected,
                    onClick = {
                        haptics.perform(HapticEvent.Tap)
                        onSelect(entry)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = ProposalAnswer.entries.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = appColors.accent,
                        activeContentColor = Color.White,
                        activeBorderColor = appColors.accent,
                        inactiveContainerColor = Color.Transparent,
                        inactiveContentColor = appColors.textPrimary,
                        inactiveBorderColor = appColors.textSecondary.copy(alpha = 0.35f)
                    )
                ) { Text(stringResource(entry.labelRes), color = if (isSelected) Color.White else appColors.textPrimary, maxLines = 1) }
            }
        }
    }
}

/** A same-style toggle + optional second [DateField] for [PeriodEntry.endDate] -- off (null) by
 *  default, since logging only the start date is the common case and an unentered end must never
 *  be read as "still ongoing" (see [PeriodEntry.endDate]). */
@Composable
private fun PeriodEndDateField(startDate: LocalDate, endDate: LocalDate?, onEndDateChange: (LocalDate?) -> Unit) {
    val haptics = LocalHaptics.current
    val appColors = appColors()
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.period_set_end_date),
                style = MaterialTheme.typography.labelLarge,
                color = appColors.textSecondary,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = endDate != null,
                onCheckedChange = { checked ->
                    haptics.perform(HapticEvent.Toggle)
                    onEndDateChange(if (checked) startDate else null)
                }
            )
        }
        if (endDate != null) {
            Spacer(Modifier.height(6.dp))
            DateField(endDate) { onEndDateChange(it) }
        }
    }
}

@Composable
internal fun CountStepper(label: String, count: Int, onCountChange: (Int) -> Unit) {
    val haptics = LocalHaptics.current
    val appColors = appColors()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = appColors.textSecondary, modifier = Modifier.weight(1f))
        IconButton(onClick = {
            if (count > 0) {
                haptics.perform(HapticEvent.Tap)
                onCountChange(count - 1)
            }
        }) { Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.stepper_less), tint = appColors.accent) }
        Text(
            count.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = appColors.textPrimary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        IconButton(onClick = {
            haptics.perform(HapticEvent.Tap)
            onCountChange(count + 1)
        }) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.stepper_more), tint = appColors.accent) }
    }
}

@Composable
private fun SheetActions(onDismiss: () -> Unit, onDelete: (() -> Unit)?, onSave: () -> Unit) {
    val appColors = appColors()
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        if (onDelete != null) {
            TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.weight(1f))
        }
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = appColors.textSecondary) }
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = onSave,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = appColors.accent, contentColor = Color.White)
        ) { Text(stringResource(R.string.action_save)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodSheet(
    initialDate: LocalDate,
    initialEndDate: LocalDate? = null,
    entry: PeriodEntry?,
    onDismiss: () -> Unit,
    onSave: (PeriodEntry) -> Unit,
    onDelete: (() -> Unit)?
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val appColors = appColors()
    var date by remember { mutableStateOf(entry?.startDate?.toLocalDateOrNull() ?: initialDate) }
    var endDate by remember { mutableStateOf(entry?.endDate?.toLocalDateOrNull() ?: initialEndDate) }
    var notes by remember { mutableStateOf(entry?.notes ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = appColors.warmSurface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                if (entry == null) stringResource(R.string.agenda_period_start) else stringResource(R.string.sheet_edit_entry),
                style = MaterialTheme.typography.titleLarge,
                color = appColors.textPrimary
            )
            DateField(date) { date = it }
            PeriodEndDateField(startDate = date, endDate = endDate) { endDate = it }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.field_note)) },
                minLines = 2,
                maxLines = 4,
                shape = sheetFieldShape,
                colors = sheetFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            SheetActions(
                onDismiss = onDismiss,
                onDelete = onDelete,
                onSave = {
                    onSave(
                        PeriodEntry(
                            id = entry?.id ?: newEventId(),
                            startDate = date.toString(),
                            endDate = endDate?.takeIf { !it.isBefore(date) }?.toString(),
                            notes = notes.trim()
                        )
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SexSheet(
    initialDate: LocalDate,
    entry: SexEntry?,
    onDismiss: () -> Unit,
    onSave: (SexEntry) -> Unit,
    onDelete: (() -> Unit)?
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val appColors = appColors()
    var date by remember { mutableStateOf(entry?.date?.toLocalDateOrNull() ?: initialDate) }
    var initiator by remember { mutableStateOf(entry?.initiator?.let(Initiator::fromStorage) ?: Initiator.ME) }
    var myOrgasmCount by remember { mutableStateOf(entry?.myOrgasmCount ?: 0) }
    var partnerOrgasmCount by remember { mutableStateOf(entry?.partnerOrgasmCount ?: 0) }
    var notes by remember { mutableStateOf(entry?.notes ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = appColors.warmSurface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                if (entry == null) stringResource(R.string.agenda_intimacy) else stringResource(R.string.sheet_edit_entry),
                style = MaterialTheme.typography.titleLarge,
                color = appColors.textPrimary
            )
            DateField(date) { date = it }
            InitiatorSelector(initiator) { initiator = it }
            CountStepper(stringResource(R.string.field_my_orgasms), myOrgasmCount) { myOrgasmCount = it }
            CountStepper(stringResource(R.string.field_partner_orgasms), partnerOrgasmCount) { partnerOrgasmCount = it }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.field_note)) },
                minLines = 2,
                maxLines = 4,
                shape = sheetFieldShape,
                colors = sheetFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            SheetActions(
                onDismiss = onDismiss,
                onDelete = onDelete,
                onSave = {
                    onSave(
                        SexEntry(
                            id = entry?.id ?: newEventId(),
                            date = date.toString(),
                            initiator = initiator.storageValue,
                            myOrgasmCount = myOrgasmCount,
                            partnerOrgasmCount = partnerOrgasmCount,
                            notes = notes.trim()
                        )
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProposalSheet(
    initialDate: LocalDate,
    entry: ProposalEntry?,
    onDismiss: () -> Unit,
    onSave: (ProposalEntry) -> Unit,
    onDelete: (() -> Unit)?
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val appColors = appColors()
    var date by remember { mutableStateOf(entry?.date?.toLocalDateOrNull() ?: initialDate) }
    var initiator by remember { mutableStateOf(entry?.initiator?.let(Initiator::fromStorage) ?: Initiator.ME) }
    var answer by remember {
        mutableStateOf(proposalAnswerOf(entry?.accepted ?: true, entry?.answered ?: true))
    }
    var declineReason by remember { mutableStateOf(entry?.declineReason ?: "") }
    var notes by remember { mutableStateOf(entry?.notes ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = appColors.warmSurface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                if (entry == null) stringResource(R.string.agenda_proposal) else stringResource(R.string.sheet_edit_entry),
                style = MaterialTheme.typography.titleLarge,
                color = appColors.textPrimary
            )
            DateField(date) { date = it }
            InitiatorSelector(initiator) { initiator = it }
            ProposalAnswerSelector(answer) { answer = it }
            if (answer == ProposalAnswer.DECLINED) {
                DeclineReasonSelector(declineReason) { declineReason = it }
            }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.field_note)) },
                minLines = 2,
                maxLines = 4,
                shape = sheetFieldShape,
                colors = sheetFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            SheetActions(
                onDismiss = onDismiss,
                onDelete = onDelete,
                onSave = {
                    onSave(
                        ProposalEntry(
                            id = entry?.id ?: newEventId(),
                            date = date.toString(),
                            initiator = initiator.storageValue,
                            accepted = answer == ProposalAnswer.ACCEPTED,
                            answered = answer != ProposalAnswer.PENDING,
                            declineReason = if (answer == ProposalAnswer.DECLINED) declineReason.trim() else "",
                            notes = notes.trim()
                        )
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasturbationSheet(
    initialDate: LocalDate,
    entry: MasturbationEntry?,
    onDismiss: () -> Unit,
    onSave: (MasturbationEntry) -> Unit,
    onDelete: (() -> Unit)?
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val appColors = appColors()
    var date by remember { mutableStateOf(entry?.date?.toLocalDateOrNull() ?: initialDate) }
    var person by remember { mutableStateOf(entry?.person?.let(Initiator::fromStorage) ?: Initiator.ME) }
    var orgasmCount by remember { mutableStateOf(entry?.orgasmCount ?: 0) }
    var notes by remember { mutableStateOf(entry?.notes ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = appColors.warmSurface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                if (entry == null) stringResource(R.string.agenda_masturbation) else stringResource(R.string.sheet_edit_entry),
                style = MaterialTheme.typography.titleLarge,
                color = appColors.textPrimary
            )
            DateField(date) { date = it }
            InitiatorSelector(person) { person = it }
            CountStepper(stringResource(R.string.field_orgasm_count), orgasmCount) { orgasmCount = it }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.field_note)) },
                minLines = 2,
                maxLines = 4,
                shape = sheetFieldShape,
                colors = sheetFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            SheetActions(
                onDismiss = onDismiss,
                onDelete = onDelete,
                onSave = {
                    onSave(
                        MasturbationEntry(
                            id = entry?.id ?: newEventId(),
                            date = date.toString(),
                            person = person.storageValue,
                            orgasmCount = orgasmCount,
                            notes = notes.trim()
                        )
                    )
                }
            )
        }
    }
}
