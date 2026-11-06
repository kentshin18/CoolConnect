package com.example.coolconnect

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.firebase.Timestamp
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportExporter {

    private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun parseNumericId(docId: String?, fallbackId: Int = 10001): Int {
        if (docId.isNullOrBlank()) return fallbackId
        val digits = docId.filter { it.isDigit() }
        return if (digits.length in 4..8) {
            digits.toInt()
        } else {
            fallbackId + Math.abs(docId.hashCode() % 89999)
        }
    }

    fun formatDateOnly(raw: Any?): String {
        return when (raw) {
            is Timestamp -> DATE_FORMAT.format(raw.toDate())
            is Date -> DATE_FORMAT.format(raw)
            is Long -> DATE_FORMAT.format(Date(raw))
            is String -> {
                val str = raw.trim()
                if (str.isEmpty()) return DATE_FORMAT.format(Date())
                val match = Regex("\\d{4}-\\d{2}-\\d{2}").find(str)
                if (match != null) {
                    match.value
                } else {
                    DATE_FORMAT.format(Date())
                }
            }
            else -> DATE_FORMAT.format(Date())
        }
    }

    // 1. Executive Master Summary Report
    fun generateMasterReportCsv(
        services: List<Map<String, Any>>,
        orders: List<ShopOrder>
    ): String {
        val exportDate = DATE_FORMAT.format(Date())
        val sb = StringBuilder()
        sb.append("COOLCONNECT HVAC PLATFORM - EXECUTIVE MASTER REPORT\n")
        sb.append("Report Date:,\"$exportDate\"\n")
        sb.append("Total Services:,\"${services.size}\",Total Orders:,\"${orders.size}\"\n\n")

        sb.append("Activity ID,Date,Category,Customer Name,Item / Service Description,Assigned Technician,Parts Used,Labor Fee,Parts Fee,VAT,Total Amount,Payment Method,Status\n")

        var activityCounter = 10001

        services.forEach { svc ->
            val id = parseNumericId(svc["docId"]?.toString() ?: svc["id"]?.toString(), activityCounter++)
            val date = formatDateOnly(svc["scheduledTimestamp"] ?: svc["timestamp"] ?: svc["completedAt"])
            val cust = svc["customerName"]?.toString() ?: "Customer"
            val issue = svc["issue"]?.toString() ?: "HVAC Service"
            val tech = svc["technician"]?.toString() ?: "Unassigned"
            val parts = (svc["partsUsed"] ?: svc["partsDetails"])?.toString() ?: "Standard / None"
            val labor = (svc["laborFee"] as? Number)?.toDouble() ?: 0.0
            val partsFee = (svc["partsFee"] as? Number)?.toDouble() ?: 0.0
            val vat = (svc["vatAmount"] as? Number)?.toDouble() ?: 0.0
            val total = (svc["totalAmount"] as? Number)?.toDouble() ?: (labor + partsFee + vat)
            val pay = svc["paymentMethod"]?.toString() ?: "Cash"
            val status = svc["status"]?.toString() ?: "Pending"

            sb.append("$id,\"$date\",\"Service Activity\",\"${escapeCsv(cust)}\",\"${escapeCsv(issue)}\",\"${escapeCsv(tech)}\",\"${escapeCsv(parts)}\",\"₱${String.format(Locale.getDefault(), "%,.2f", labor)}\",\"₱${String.format(Locale.getDefault(), "%,.2f", partsFee)}\",\"₱${String.format(Locale.getDefault(), "%,.2f", vat)}\",\"₱${String.format(Locale.getDefault(), "%,.2f", total)}\",\"${escapeCsv(pay)}\",\"${escapeCsv(status)}\"\n")
        }

        orders.forEach { ord ->
            val id = parseNumericId(ord.orderId, activityCounter++)
            val date = formatDateOnly(ord.timestamp)
            val cust = ord.customerName
            val itemsStr = ord.itemsSummary.ifBlank { ord.items.joinToString("; ") { it["name"]?.toString() ?: "Hardware Item" } }
            val tech = ord.assignedTechnician.ifBlank { "Admin Logistics" }
            val vat = ord.vat
            val total = ord.total
            val subtotal = ord.subtotal
            val pay = ord.paymentMethod.ifBlank { "GCash" }
            val status = ord.status

            sb.append("$id,\"$date\",\"Hardware Order\",\"${escapeCsv(cust)}\",\"${escapeCsv(itemsStr)}\",\"${escapeCsv(tech)}\",\"${escapeCsv(itemsStr)}\",\"₱0.00\",\"₱${String.format(Locale.getDefault(), "%,.2f", subtotal)}\",\"₱${String.format(Locale.getDefault(), "%,.2f", vat)}\",\"₱${String.format(Locale.getDefault(), "%,.2f", total)}\",\"${escapeCsv(pay)}\",\"${escapeCsv(status)}\"\n")
        }

        return sb.toString()
    }

    // 2. Sales, Revenue & Financial Accounting Report
    fun generateFinancialReportCsv(
        services: List<Map<String, Any>>,
        orders: List<ShopOrder>
    ): String {
        val sb = StringBuilder()
        sb.append("COOLCONNECT FINANCIAL ACCOUNTING & REVENUE REPORT\n")
        sb.append("Report Date:,\"${DATE_FORMAT.format(Date())}\"\n\n")

        sb.append("Activity ID,Date,Revenue Stream,Customer Name,Labor Revenue,Parts & Hardware Sales,VAT,Gross Total,Payment Method,Payment Status\n")

        var txCounter = 20001

        services.forEach { svc ->
            val id = parseNumericId(svc["docId"]?.toString() ?: svc["id"]?.toString(), txCounter++)
            val date = formatDateOnly(svc["completedAt"] ?: svc["timestamp"])
            val cust = svc["customerName"]?.toString() ?: "Customer"
            val labor = (svc["laborFee"] as? Number)?.toDouble() ?: 0.0
            val partsFee = (svc["partsFee"] as? Number)?.toDouble() ?: 0.0
            val vat = (svc["vatAmount"] as? Number)?.toDouble() ?: 0.0
            val total = (svc["totalAmount"] as? Number)?.toDouble() ?: (labor + partsFee + vat)
            val pay = svc["paymentMethod"]?.toString() ?: "GCash"
            val status = svc["status"]?.toString() ?: "Completed"

            sb.append("$id,\"$date\",\"Services Operations\",\"${escapeCsv(cust)}\",\"₱${String.format(Locale.getDefault(), "%,.2f", labor)}\",\"₱${String.format(Locale.getDefault(), "%,.2f", partsFee)}\",\"₱${String.format(Locale.getDefault(), "%,.2f", vat)}\",\"₱${String.format(Locale.getDefault(), "%,.2f", total)}\",\"${escapeCsv(pay)}\",\"${escapeCsv(status)}\"\n")
        }

        orders.forEach { ord ->
            val id = parseNumericId(ord.orderId, txCounter++)
            val date = formatDateOnly(ord.timestamp)
            val cust = ord.customerName
            val partsSales = ord.subtotal
            val vat = ord.vat
            val total = ord.total
            val pay = ord.paymentMethod.ifBlank { "QR Ph" }
            val status = ord.status

            sb.append("$id,\"$date\",\"Hardware E-Commerce\",\"${escapeCsv(cust)}\",\"₱0.00\",\"₱${String.format(Locale.getDefault(), "%,.2f", partsSales)}\",\"₱${String.format(Locale.getDefault(), "%,.2f", vat)}\",\"₱${String.format(Locale.getDefault(), "%,.2f", total)}\",\"${escapeCsv(pay)}\",\"${escapeCsv(status)}\"\n")
        }

        return sb.toString()
    }

    // 3. Technician Services & Operations Log
    fun generateTechnicianOperationsCsv(services: List<Map<String, Any>>): String {
        val sb = StringBuilder()
        sb.append("COOLCONNECT TECHNICIAN DISPATCH & OPERATIONS REPORT\n")
        sb.append("Report Date:,\"${DATE_FORMAT.format(Date())}\"\n\n")

        sb.append("Activity ID,Date,Service Type,Assigned Technician,Customer Name,Parts Used,Labor Charge,Parts Charge,Total Amount,Status,Technician Notes\n")

        var dispatchCounter = 30001

        services.forEach { svc ->
            val id = parseNumericId(svc["docId"]?.toString() ?: svc["id"]?.toString(), dispatchCounter++)
            val date = formatDateOnly(svc["scheduledTimestamp"] ?: svc["timestamp"])
            val issue = svc["issue"]?.toString() ?: "HVAC Repair"
            val tech = svc["technician"]?.toString() ?: "Unassigned"
            val cust = svc["customerName"]?.toString() ?: "Customer"
            val parts = (svc["partsUsed"] ?: svc["partsDetails"])?.toString() ?: "Standard / None"
            val labor = (svc["laborFee"] as? Number)?.toDouble() ?: 0.0
            val partsFee = (svc["partsFee"] as? Number)?.toDouble() ?: 0.0
            val total = (svc["totalAmount"] as? Number)?.toDouble() ?: (labor + partsFee)
            val status = svc["status"]?.toString() ?: "Pending"
            val notes = (svc["completionNotes"] ?: svc["notes"])?.toString() ?: "None"

            sb.append("$id,\"$date\",\"${escapeCsv(issue)}\",\"${escapeCsv(tech)}\",\"${escapeCsv(cust)}\",\"${escapeCsv(parts)}\",\"₱${String.format(Locale.getDefault(), "%,.2f", labor)}\",\"₱${String.format(Locale.getDefault(), "%,.2f", partsFee)}\",\"₱${String.format(Locale.getDefault(), "%,.2f", total)}\",\"${escapeCsv(status)}\",\"${escapeCsv(notes)}\"\n")
        }

        return sb.toString()
    }

    // 4. Customer Hardware Orders & Parts Catalog Purchases
    fun generateHardwareOrdersCsv(orders: List<ShopOrder>): String {
        val sb = StringBuilder()
        sb.append("COOLCONNECT CUSTOMER HARDWARE & PARTS PURCHASES REPORT\n")
        sb.append("Report Date:,\"${DATE_FORMAT.format(Date())}\"\n\n")

        sb.append("Activity ID,Date,Customer Name,Phone,Delivery Address,Ordered Products & Parts,Items Count,Subtotal,VAT (12%),Total Amount,Payment Method,Status\n")

        var orderCounter = 40001

        orders.forEach { ord ->
            val id = parseNumericId(ord.orderId, orderCounter++)
            val date = formatDateOnly(ord.timestamp)
            val cust = ord.customerName
            val phone = ord.customerPhone
            val addr = ord.shippingAddress
            val itemsStr = ord.itemsSummary.ifBlank { ord.items.joinToString("; ") { "${it["quantity"]}x ${it["name"]}" } }
            val count = ord.items.sumOf { (it["quantity"] as? Number)?.toInt() ?: 1 }
            val sub = ord.subtotal
            val vat = ord.vat
            val total = ord.total
            val pay = ord.paymentMethod
            val status = ord.status

            sb.append("$id,\"$date\",\"${escapeCsv(cust)}\",\"${escapeCsv(phone)}\",\"${escapeCsv(addr)}\",\"${escapeCsv(itemsStr)}\",$count,\"₱${String.format(Locale.getDefault(), "%,.2f", sub)}\",\"₱${String.format(Locale.getDefault(), "%,.2f", vat)}\",\"₱${String.format(Locale.getDefault(), "%,.2f", total)}\",\"${escapeCsv(pay)}\",\"${escapeCsv(status)}\"\n")
        }

        return sb.toString()
    }

    // 5. Customer Directory & Lifetime Account Summary
    fun generateCustomerDirectoryCsv(users: List<Map<String, Any>>): String {
        val sb = StringBuilder()
        sb.append("COOLCONNECT REGISTERED CUSTOMERS DIRECTORY REPORT\n")
        sb.append("Report Date:,\"${DATE_FORMAT.format(Date())}\"\n\n")

        sb.append("Activity ID,Date,Customer Name,Email,Phone Number,City/Region,Full Address,CoolConnect+ Plan,KYC Status\n")

        var custCounter = 50001

        users.filter { (it["role"]?.toString() ?: "customer") == "customer" }.forEach { usr ->
            val id = parseNumericId(usr["uid"]?.toString(), custCounter++)
            val date = formatDateOnly(usr["timestamp"] ?: usr["createdAt"])
            val name = usr["name"]?.toString() ?: "Customer"
            val email = usr["email"]?.toString() ?: "N/A"
            val phone = usr["phone"]?.toString() ?: "N/A"
            val city = usr["city"]?.toString() ?: usr["province"]?.toString() ?: "Metro Manila"
            val addr = usr["address"]?.toString() ?: "No address saved"
            val plan = usr["subscriptionStatus"]?.toString() ?: "Free Tier"
            val kyc = usr["verificationStatus"]?.toString() ?: "Verified"

            sb.append("$id,\"$date\",\"${escapeCsv(name)}\",\"${escapeCsv(email)}\",\"${escapeCsv(phone)}\",\"${escapeCsv(city)}\",\"${escapeCsv(addr)}\",\"${escapeCsv(plan)}\",\"${escapeCsv(kyc)}\"\n")
        }

        return sb.toString()
    }

    // 6. Hardware Inventory & Stock Audit Report
    fun generateInventoryReportCsv(products: List<Map<String, Any>>): String {
        val sb = StringBuilder()
        sb.append("COOLCONNECT HARDWARE & PARTS INVENTORY AUDIT REPORT\n")
        sb.append("Report Date:,\"${DATE_FORMAT.format(Date())}\"\n\n")

        sb.append("Activity ID,Date,Part / Product Name,Category,Unit Price (₱),Stock Available,Inventory Status,Warranty\n")

        var itemCounter = 60001

        products.forEach { prod ->
            val id = parseNumericId(prod["id"]?.toString(), itemCounter++)
            val date = formatDateOnly(prod["timestamp"])
            val name = prod["name"]?.toString() ?: "Part"
            val cat = prod["category"]?.toString() ?: "HVAC Parts"
            val rawPrice = (prod["price"] as? Number)?.toDouble() ?: 0.0
            val priceStr = String.format(Locale.getDefault(), "%,.2f", rawPrice)
            val stock = (prod["stock"] as? Number)?.toInt() ?: 10
            val badge = if (stock <= 0) "OUT OF STOCK" else (prod["badge"]?.toString() ?: "IN STOCK")
            val warranty = prod["warranty"]?.toString() ?: "1 Year Warranty"

            sb.append("$id,\"$date\",\"${escapeCsv(name)}\",\"${escapeCsv(cat)}\",\"$priceStr\",$stock,\"${escapeCsv(badge)}\",\"${escapeCsv(warranty)}\"\n")
        }

        return sb.toString()
    }

    // General Exporter Function
    fun exportReportToFile(context: Context, fileNamePrefix: String, csvContent: String): Uri? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "${fileNamePrefix}_$timeStamp.csv"
        return saveFileToDownloads(context, fileName, csvContent, "text/csv")
    }

    fun exportSingleReceiptToFile(context: Context, customerName: String, service: Map<String, Any>): Uri? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val rawDocId = service["docId"]?.toString() ?: timeStamp
        val docIdNumber = parseNumericId(rawDocId, 90001)
        val fileName = "CoolConnect_Receipt_$docIdNumber.txt"

        val techName = service["technician"]?.toString() ?: "Technician"
        val issue = service["issue"]?.toString() ?: "Service"
        val payMethod = service["paymentMethod"]?.toString() ?: "Cash"
        val labor = (service["laborFee"] as? Number)?.toDouble() ?: 500.0
        val partsFee = (service["partsFee"] as? Number)?.toDouble() ?: 0.0
        val partsUsed = (service["partsUsed"] ?: service["partsDetails"])?.toString()
        val vat = (service["vatAmount"] as? Number)?.toDouble() ?: 0.0
        val total = (service["totalAmount"] as? Number)?.toDouble() ?: (labor + partsFee + vat)
        val notes = (service["completionNotes"] ?: service["notes"])?.toString() ?: "None"
        val dateStr = formatDateOnly(service["completedAt"] ?: service["timestamp"])

        val receiptContent = """
            ====================================================
                       COOLCONNECT OFFICIAL E-RECEIPT
            ====================================================
            Receipt Number: $docIdNumber
            Date: $dateStr
            Customer: $customerName
            Technician: $techName
            
            SERVICE DETAILS:
            Activity/Issue: $issue
            Payment Method: $payMethod
            Status: COMPLETED (PAID)
            
            PARTS & MATERIALS USED:
            ${if (!partsUsed.isNullOrBlank()) partsUsed else if (partsFee > 0) "Parts Charge: ₱${String.format(Locale.getDefault(), "%,.2f", partsFee)}" else "Standard Equipment / None"}
            
            TECHNICIAN NOTES & RECOMMENDATIONS:
            $notes
            
            FINANCIAL BREAKDOWN:
            ----------------------------------------------------
            Labor & Service Fee:  ₱${String.format(Locale.getDefault(), "%,.2f", labor)}
            Parts & Hardware Fee: ₱${String.format(Locale.getDefault(), "%,.2f", partsFee)}
            ----------------------------------------------------
            TOTAL AMOUNT PAID:    ₱${String.format(Locale.getDefault(), "%,.2f", total)}
            ====================================================
            Thank you for choosing CoolConnect HVAC Services!
        """.trimIndent()

        return saveFileToDownloads(context, fileName, receiptContent, "text/plain")
    }

    private fun saveFileToDownloads(context: Context, fileName: String, content: String, mimeType: String): Uri? {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(content.toByteArray(Charsets.UTF_8))
                    }
                    Toast.makeText(context, "Saved to Downloads: $fileName", Toast.LENGTH_LONG).show()
                    promptOpenFile(context, uri, mimeType)
                    return uri
                }
            }

            // Fallback for API < 29
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val file = File(downloadsDir, fileName)
            FileOutputStream(file).use { fos ->
                fos.write(content.toByteArray(Charsets.UTF_8))
            }
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mimeType), null)

            val fileUri = try {
                FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            } catch (e: Exception) {
                Uri.fromFile(file)
            }

            Toast.makeText(context, "Saved to Downloads: ${file.name}", Toast.LENGTH_LONG).show()
            promptOpenFile(context, fileUri, mimeType)
            return fileUri
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error saving file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            return null
        }
    }

    private fun promptOpenFile(context: Context, uri: Uri, mimeType: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Open Downloaded Report File"))
        } catch (_: Exception) {
            // If no default app, file remains in Downloads
        }
    }

    private fun escapeCsv(value: String): String {
        return value.replace("\"", "\"\"").replace("\n", " ").replace("\r", "")
    }
}
