package com.example.coolconnect

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coolconnect.ui.theme.*
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

private fun normalizeStrokes(
    strokes: List<List<Offset>>,
    targetWidth: Int,
    targetHeight: Int,
    padding: Float = 20f
): List<List<Offset>> {
    val points = strokes.flatten()
    if (points.isEmpty()) return emptyList()

    val minX = points.minOf { it.x }
    val maxX = points.maxOf { it.x }
    val minY = points.minOf { it.y }
    val maxY = points.maxOf { it.y }

    val spanX = (maxX - minX).coerceAtLeast(1f)
    val spanY = (maxY - minY).coerceAtLeast(1f)
    val scale = minOf(
        (targetWidth - 2 * padding) / spanX,
        (targetHeight - 2 * padding) / spanY
    )

    return strokes.map { stroke ->
        stroke.map { p ->
            Offset(
                x = padding + (p.x - minX) * scale,
                y = padding + (p.y - minY) * scale
            )
        }
    }
}

private suspend fun uploadBitmapSuspend(
    viewModel: TechnicianViewModel,
    bitmap: Bitmap,
    folder: String
): Result<String> = suspendCancellableCoroutine { cont ->
    viewModel.uploadBitmapImage(bitmap, folder) { result ->
        cont.resume(result)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobCompletionScreen(
    job: TechJobDetail,
    techName: String,
    viewModel: TechnicianViewModel,
    onExit: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    var showErrors by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val draft = remember { CompletionDraft() }

    BackHandler(enabled = isSubmitting) { }

    fun canProceed(currentStep: Int): Boolean {
        return when (currentStep) {
            0 -> draft.checklist.values.all { it }
            1 -> draft.workNotes.trim().isNotBlank()
            2 -> draft.partsFeeInput.toDoubleOrNull().let { it != null && it >= 0.0 }
            3 -> draft.laborFeeInput.toDoubleOrNull().let { it != null && it > 0.0 }
            4 -> draft.signatureStrokes.isNotEmpty()
            5 -> true
            else -> true
        }
    }

    fun performSubmit() {
        if (isSubmitting) return

        if (draft.signatureStrokes.isEmpty()) {
            submitError = "Customer signature is required."
            return
        }
        if (draft.fees.labor <= 0.0) {
            submitError = "Labor fee is required and must be greater than ₱0.00."
            return
        }

        submitError = null
        isSubmitting = true

        scope.launch {
            val sigBitmap = createSignatureBitmap(normalizeStrokes(draft.signatureStrokes, 500, 250))
            val sigResult = uploadBitmapSuspend(viewModel, sigBitmap, "signatures")
            if (sigResult.isFailure) {
                val msg = sigResult.exceptionOrNull()?.localizedMessage ?: "Unknown error"
                submitError = "Signature upload failed: $msg"
                isSubmitting = false
                return@launch
            }
            val signatureUrl = sigResult.getOrNull() ?: ""

            var photoProofUrl = ""
            val photoBmp = draft.photoBitmap
            if (photoBmp != null) {
                val photoResult = uploadBitmapSuspend(viewModel, photoBmp, "job_proofs")
                if (photoResult.isFailure) {
                    val msg = photoResult.exceptionOrNull()?.localizedMessage ?: "Unknown error"
                    submitError = "Photo upload failed: $msg"
                    isSubmitting = false
                    return@launch
                }
                photoProofUrl = photoResult.getOrNull() ?: ""
            }

            viewModel.completeJob(
                jobId = job.id,
                workNotes = draft.workNotes.trim(),
                partsUsed = draft.partsUsed.trim(),
                laborFee = draft.fees.labor,
                partsFee = draft.fees.parts,
                vatAmount = draft.fees.vat,
                totalAmount = draft.fees.total,
                paymentMethod = draft.paymentMethod,
                photoProofUrl = photoProofUrl,
                signatureUrl = signatureUrl,
                techName = techName,
                onComplete = { success, errorMsg ->
                    isSubmitting = false
                    if (success) {
                        onExit()
                    } else {
                        submitError = "Failed to complete job: ${errorMsg ?: "Unknown error"}"
                    }
                }
            )
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            draft.photoBitmap = bitmap
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Service Report",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextDark
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (!isSubmitting) onExit() }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextDark
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            Surface(
                color = Color.White,
                shadowElevation = 8.dp,
                border = BorderStroke(0.5.dp, FieldBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (submitError != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF2F2)),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color.Red)
                        ) {
                            Text(
                                text = submitError!!,
                                color = Color.Red,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                showErrors = false
                                submitError = null
                                if (step > 0) step--
                            },
                            enabled = step > 0 && !isSubmitting,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Back", fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        if (step == 5) {
                            Button(
                                onClick = { performSubmit() },
                                enabled = !isSubmitting,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PrimaryGreen,
                                    disabledContainerColor = PrimaryGreen.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isSubmitting) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Submitting...", fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                } else {
                                    Text("Submit", fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Button(
                                onClick = {
                                    if (canProceed(step)) {
                                        showErrors = false
                                        submitError = null
                                        step++
                                    } else {
                                        showErrors = true
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Next", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        containerColor = AppLightBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Step Progress Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Customer: ${job.name}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryBlue
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (step + 1) / 6f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = PrimaryBlue,
                    trackColor = AppLightBg
                )
            }

            // Step Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                when (step) {
                    0 -> {
                        // Step 0: Checklist
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "Service Checklist",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                            val completedCount = draft.checklist.values.count { it }
                            val totalCount = draft.checklist.size
                            Text(
                                text = "$completedCount of $totalCount completed",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (completedCount == totalCount) PrimaryGreen else Color.Gray
                            )

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, FieldBorder)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    draft.checklist.keys.toList().forEach { item ->
                                        val checked = draft.checklist[item] ?: false
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { draft.checklist[item] = !checked }
                                                .padding(vertical = 8.dp)
                                        ) {
                                            Checkbox(
                                                checked = checked,
                                                onCheckedChange = { draft.checklist[item] = it },
                                                colors = CheckboxDefaults.colors(checkedColor = PrimaryGreen)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = item,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = TextDark
                                            )
                                        }
                                    }
                                }
                            }

                            if (showErrors && !canProceed(0)) {
                                Text(
                                    text = "Please complete all checklist items before proceeding.",
                                    color = Color.Red,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    1 -> {
                        // Step 1: Work Notes
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "Work Notes & Summary",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                            Text(
                                text = "Describe the service performed, diagnostic results, or recommendations.",
                                fontSize = 13.sp,
                                color = Color.Gray
                            )

                            OutlinedTextField(
                                value = draft.workNotes,
                                onValueChange = { draft.workNotes = it },
                                label = { Text("Service Summary & Recommendations *") },
                                placeholder = { Text("e.g. Replaced 45uF capacitor, cleaned coil filters...") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 140.dp),
                                maxLines = 6,
                                shape = RoundedCornerShape(12.dp),
                                isError = showErrors && !canProceed(1),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PrimaryBlue,
                                    unfocusedBorderColor = FieldBorder,
                                    errorBorderColor = Color.Red
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text(
                                    text = "${draft.workNotes.length} characters",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }

                            if (showErrors && !canProceed(1)) {
                                Text(
                                    text = "Service summary notes are required.",
                                    color = Color.Red,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    2 -> {
                        // Step 2: Parts Used
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "Parts & Materials Used",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                            Text(
                                text = "Record any spare parts, refrigerants, or supplies used during service.",
                                fontSize = 13.sp,
                                color = Color.Gray
                            )

                            OutlinedTextField(
                                value = draft.partsUsed,
                                onValueChange = { draft.partsUsed = it },
                                label = { Text("Parts & Materials (Optional)") },
                                placeholder = { Text("e.g. 45uF Capacitor, R410A Refrigerant (1.5kg)") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 100.dp),
                                maxLines = 4,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PrimaryBlue,
                                    unfocusedBorderColor = FieldBorder
                                )
                            )

                            OutlinedTextField(
                                value = draft.partsFeeInput,
                                onValueChange = { draft.partsFeeInput = it },
                                label = { Text("Parts Fee (₱)") },
                                placeholder = { Text("0") },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = RoundedCornerShape(12.dp),
                                isError = showErrors && !canProceed(2),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PrimaryBlue,
                                    unfocusedBorderColor = FieldBorder,
                                    errorBorderColor = Color.Red
                                )
                            )

                            if (showErrors && !canProceed(2)) {
                                Text(
                                    text = "Parts fee must be a valid non-negative number.",
                                    color = Color.Red,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    3 -> {
                        // Step 3: Billing
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "Billing & Payment Method",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )

                            OutlinedTextField(
                                value = draft.laborFeeInput,
                                onValueChange = { draft.laborFeeInput = it },
                                label = { Text("Labor Fee (₱) *") },
                                placeholder = { Text("e.g. 500") },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = RoundedCornerShape(12.dp),
                                isError = showErrors && !canProceed(3),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PrimaryBlue,
                                    unfocusedBorderColor = FieldBorder,
                                    errorBorderColor = Color.Red
                                )
                            )

                            if (showErrors && !canProceed(3)) {
                                Text(
                                    text = "Labor fee is required and must be greater than ₱0.00.",
                                    color = Color.Red,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Text(
                                text = "FEE BREAKDOWN",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.Gray,
                                letterSpacing = 1.sp
                            )

                            val fees = draft.fees
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, FieldBorder)
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Labor Fee", fontSize = 13.sp, color = Color.Gray)
                                        Text("₱${String.format(Locale.getDefault(), "%.2f", fees.labor)}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Parts Fee", fontSize = 13.sp, color = Color.Gray)
                                        Text("₱${String.format(Locale.getDefault(), "%.2f", fees.parts)}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = FieldBorder)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Total Amount", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextDark)
                                        Text("₱${String.format(Locale.getDefault(), "%.2f", fees.total)}", fontSize = 16.sp, fontWeight = FontWeight.Black, color = PrimaryGreen)
                                    }
                                }
                            }

                            Text(
                                text = "Payment Method",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = TextDark
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("QR Ph", "GCash", "Maya", "Cash").forEach { method ->
                                    val isSel = draft.paymentMethod == method
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { draft.paymentMethod = method },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSel) PrimaryBlue.copy(alpha = 0.12f) else Color.White,
                                        border = BorderStroke(1.dp, if (isSel) PrimaryBlue else FieldBorder)
                                    ) {
                                        Text(
                                            text = method,
                                            modifier = Modifier.padding(vertical = 10.dp),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSel) PrimaryBlue else TextDark,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }

                    4 -> {
                        // Step 4: Proof & Signature (scrolling disabled to prevent gesture conflict with signature pad)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "Photo Proof & E-Signature",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )

                            Text("Photo Verification Proof (Optional)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = { cameraLauncher.launch(null) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                    border = BorderStroke(1.dp, PrimaryBlue),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (draft.photoBitmap == null) "Snap Photo Proof" else "Retake Photo",
                                        color = PrimaryBlue,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                draft.photoBitmap?.let { bmp ->
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Photo Proof Preview",
                                        modifier = Modifier
                                            .size(96.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .border(1.5.dp, PrimaryGreen, RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Customer E-Signature *", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                                if (draft.signatureStrokes.isNotEmpty()) {
                                    TextButton(onClick = { draft.signatureStrokes.clear() }) {
                                        Text("Clear Signature", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .background(Color.White, RoundedCornerShape(14.dp))
                                    .border(1.5.dp, PrimaryBlue.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                                    .pointerInput(Unit) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                val newStroke = mutableStateListOf<Offset>().apply { add(offset) }
                                                draft.signatureStrokes.add(newStroke)
                                            },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                if (draft.signatureStrokes.isNotEmpty()) {
                                                    draft.signatureStrokes.last().add(change.position)
                                                }
                                            }
                                        )
                                    }
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    draft.signatureStrokes.forEach { stroke ->
                                        if (stroke.size > 1) {
                                            for (i in 0 until stroke.size - 1) {
                                                drawLine(
                                                    color = PrimaryBlue,
                                                    start = stroke[i],
                                                    end = stroke[i + 1],
                                                    strokeWidth = 6f,
                                                    cap = StrokeCap.Round
                                                )
                                            }
                                        } else if (stroke.size == 1) {
                                            drawCircle(
                                                color = PrimaryBlue,
                                                radius = 3f,
                                                center = stroke[0]
                                            )
                                        }
                                    }
                                }

                                if (draft.signatureStrokes.isEmpty()) {
                                    Text(
                                        text = "Sign here using your finger",
                                        color = Color.Gray.copy(alpha = 0.6f),
                                        fontSize = 13.sp,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                }
                            }

                            if (showErrors && !canProceed(4)) {
                                Text(
                                    text = "Customer signature is required.",
                                    color = Color.Red,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    5 -> {
                        // Step 5: Review
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "Review Service Report",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, FieldBorder)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Customer Name", fontSize = 12.sp, color = Color.Gray)
                                        Text(job.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Job ID", fontSize = 12.sp, color = Color.Gray)
                                        Text(job.id.take(8), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextDark)
                                    }

                                    val completedCount = draft.checklist.values.count { it }
                                    val totalCount = draft.checklist.size
                                    val isAllChecked = completedCount == totalCount
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Checklist", fontSize = 12.sp, color = Color.Gray)
                                        Text(
                                            text = if (isAllChecked) "Completed ($completedCount/$totalCount)" else "Incomplete ($completedCount/$totalCount)",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isAllChecked) PrimaryGreen else Color.Red
                                        )
                                    }

                                    HorizontalDivider(color = FieldBorder)

                                    Text("WORK NOTES", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.Gray, letterSpacing = 1.sp)
                                    Text(draft.workNotes.ifBlank { "None" }, fontSize = 13.sp, color = TextDark)

                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("PARTS USED", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.Gray, letterSpacing = 1.sp)
                                    Text(draft.partsUsed.ifBlank { "None" }, fontSize = 13.sp, color = TextDark)

                                    HorizontalDivider(color = FieldBorder)

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Payment Method", fontSize = 12.sp, color = Color.Gray)
                                        Text(draft.paymentMethod, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextDark)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Photo Proof Attached", fontSize = 12.sp, color = Color.Gray)
                                        Text(
                                            text = if (draft.photoBitmap != null) "Yes" else "No",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (draft.photoBitmap != null) PrimaryGreen else Color.Gray
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Signature Attached", fontSize = 12.sp, color = Color.Gray)
                                        Text(
                                            text = if (draft.signatureStrokes.isNotEmpty()) "Yes" else "No",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (draft.signatureStrokes.isNotEmpty()) PrimaryGreen else Color.Red
                                        )
                                    }

                                    HorizontalDivider(color = FieldBorder)

                                    Text("FEE BREAKDOWN", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.Gray, letterSpacing = 1.sp)
                                    val fees = draft.fees
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Labor Fee", fontSize = 12.sp, color = Color.Gray)
                                        Text("₱${String.format(Locale.getDefault(), "%.2f", fees.labor)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Parts Fee", fontSize = 12.sp, color = Color.Gray)
                                        Text("₱${String.format(Locale.getDefault(), "%.2f", fees.parts)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Total Amount", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextDark)
                                        Text("₱${String.format(Locale.getDefault(), "%.2f", fees.total)}", fontSize = 16.sp, fontWeight = FontWeight.Black, color = PrimaryGreen)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
