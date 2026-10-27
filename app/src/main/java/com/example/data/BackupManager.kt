package com.example.data

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.util.Base64
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.OutputStream
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@JsonClass(generateAdapter = true)
data class PocketBackupPayload(
    val formatVersion: String = "pocket-backup-v1",
    val exportedAtMillis: Long = System.currentTimeMillis(),
    val wallets: List<WalletEntity>,
    val categories: List<CategoryEntity>,
    val transactions: List<TransactionEntity>,
    val transfers: List<TransferEntity>,
    val loans: List<LoanEntity>,
    val loanActivities: List<LoanActivityEntity>,
    val adjustments: List<BalanceAdjustmentEntity>,
    val settings: AppSettingsEntity,
    val recurringTransactions: List<RecurringTransactionEntity> = emptyList(),
    val savingsGoals: List<SavingsGoalEntity> = emptyList()
)

@JsonClass(generateAdapter = true)
data class EncryptedBackupEnvelope(
    val formatVersion: String = "pocket-backup-v1-encrypted",
    val exportedAtMillis: Long,
    val saltBase64: String,
    val ivBase64: String,
    val cipherTextBase64: String
)

data class BackupValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null,
    val payload: PocketBackupPayload? = null,
    val isEncrypted: Boolean = false
)

object BackupManager {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val payloadAdapter = moshi.adapter(PocketBackupPayload::class.java).indent("  ")
    private val envelopeAdapter = moshi.adapter(EncryptedBackupEnvelope::class.java).indent("  ")

    fun exportToJson(
        payload: PocketBackupPayload,
        passphrase: String? = null
    ): String {
        val plainJson = payloadAdapter.toJson(payload)
        if (passphrase.isNullOrBlank()) {
            return plainJson
        }
        val random = SecureRandom()
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val key = deriveAesKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val cipherBytes = cipher.doFinal(plainJson.toByteArray(Charsets.UTF_8))

        val envelope = EncryptedBackupEnvelope(
            exportedAtMillis = payload.exportedAtMillis,
            saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
            cipherTextBase64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
        )
        return envelopeAdapter.toJson(envelope)
    }

    fun validateAndParseBackup(
        rawJson: String,
        passphrase: String? = null
    ): BackupValidationResult {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) {
            return BackupValidationResult(false, "Backup content is empty.")
        }

        val plainJson = if (trimmed.contains("\"pocket-backup-v1-encrypted\"")) {
            if (passphrase.isNullOrBlank()) {
                return BackupValidationResult(
                    isValid = false,
                    errorMessage = "This backup is encrypted. Please enter the backup passphrase.",
                    isEncrypted = true
                )
            }
            try {
                val envelope = envelopeAdapter.fromJson(trimmed)
                    ?: return BackupValidationResult(false, "Invalid encrypted backup envelope.")
                val salt = Base64.decode(envelope.saltBase64, Base64.NO_WRAP)
                val iv = Base64.decode(envelope.ivBase64, Base64.NO_WRAP)
                val cipherBytes = Base64.decode(envelope.cipherTextBase64, Base64.NO_WRAP)
                val key = deriveAesKey(passphrase, salt)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
                String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
            } catch (e: Exception) {
                return BackupValidationResult(
                    isValid = false,
                    errorMessage = "Decryption failed. Incorrect passphrase or corrupted file.",
                    isEncrypted = true
                )
            }
        } else {
            trimmed
        }

        val payload = try {
            payloadAdapter.fromJson(plainJson)
        } catch (e: Exception) {
            return BackupValidationResult(false, "Invalid JSON format: ${e.localizedMessage ?: "parse error"}")
        } ?: return BackupValidationResult(false, "Could not parse backup payload.")

        if (payload.formatVersion != "pocket-backup-v1") {
            return BackupValidationResult(
                false,
                "Unsupported backup version: ${payload.formatVersion}. Expected pocket-backup-v1."
            )
        }

        // Referential integrity validation
        val walletIds = payload.wallets.map { it.id }.toSet()
        if (walletIds.size != payload.wallets.size) {
            return BackupValidationResult(false, "Backup contains duplicate wallet IDs.")
        }

        val categoryIds = payload.categories.map { it.id }.toSet()
        if (categoryIds.size != payload.categories.size) {
            return BackupValidationResult(false, "Backup contains duplicate category IDs.")
        }

        val loanIds = payload.loans.map { it.id }.toSet()
        if (loanIds.size != payload.loans.size) {
            return BackupValidationResult(false, "Backup contains duplicate loan IDs.")
        }

        payload.transactions.forEach { tx ->
            if (tx.walletId !in walletIds) {
                return BackupValidationResult(
                    false,
                    "Referential integrity error: Transaction #${tx.id} references missing wallet #${tx.walletId}."
                )
            }
            if (tx.categoryId !in categoryIds) {
                return BackupValidationResult(
                    false,
                    "Referential integrity error: Transaction #${tx.id} references missing category #${tx.categoryId}."
                )
            }
            if (tx.amount <= 0.0) {
                return BackupValidationResult(
                    false,
                    "Validation error: Transaction #${tx.id} has non-positive amount (${tx.amount})."
                )
            }
        }

        payload.transfers.forEach { tr ->
            if (tr.sourceWalletId !in walletIds || tr.destinationWalletId !in walletIds) {
                return BackupValidationResult(
                    false,
                    "Referential integrity error: Transfer #${tr.id} references a missing wallet."
                )
            }
            if (tr.sourceWalletId == tr.destinationWalletId) {
                return BackupValidationResult(
                    false,
                    "Validation error: Transfer #${tr.id} has identical source and destination wallet."
                )
            }
        }

        payload.loans.forEach { loan ->
            if (loan.initialWalletId !in walletIds) {
                return BackupValidationResult(
                    false,
                    "Referential integrity error: Loan #${loan.id} references missing wallet #${loan.initialWalletId}."
                )
            }
        }

        payload.loanActivities.forEach { act ->
            if (act.loanId !in loanIds) {
                return BackupValidationResult(
                    false,
                    "Referential integrity error: Loan activity #${act.id} references missing loan #${act.loanId}."
                )
            }
            if (act.walletId !in walletIds) {
                return BackupValidationResult(
                    false,
                    "Referential integrity error: Loan activity #${act.id} references missing wallet #${act.walletId}."
                )
            }
        }

        payload.adjustments.forEach { adj ->
            if (adj.walletId !in walletIds) {
                return BackupValidationResult(
                    false,
                    "Referential integrity error: Balance adjustment #${adj.id} references missing wallet #${adj.walletId}."
                )
            }
        }

        return BackupValidationResult(
            isValid = true,
            payload = payload
        )
    }

    fun exportStatementToCsv(statement: MonthlyStatementSummary): String {
        val sb = StringBuilder()
        sb.appendLine("# Pocket Monthly Statement - ${statement.periodLabel} (${statement.walletFilterLabel})")
        sb.appendLine("# Opening Balance,${statement.openingBalance}")
        sb.appendLine("# Total Income,${statement.totalIncome}")
        sb.appendLine("# Total Expenses,${statement.totalExpenses}")
        sb.appendLine("# Transfers In,${statement.transfersIn}")
        sb.appendLine("# Transfers Out,${statement.transfersOut}")
        sb.appendLine("# Transfer Fees,${statement.transferFeesIncludedInExpenses}")
        sb.appendLine("# Loan Principal In,${statement.loanPrincipalReceived}")
        sb.appendLine("# Loan Principal Out,${statement.loanPrincipalRepaid}")
        sb.appendLine("# Adjustments Net,${statement.adjustmentsNet}")
        sb.appendLine("# Closing Balance,${statement.closingBalance}")
        sb.appendLine("Date,Description,Wallet,Type,Amount,Running Balance")
        statement.rows.forEach { row ->
            sb.appendLine(
                "${escapeCsv(row.dateFormatted)},${escapeCsv(row.description)},${escapeCsv(row.walletName)},${escapeCsv(row.typeLabel)},${row.signedAmount},${row.runningBalance}"
            )
        }
        return sb.toString()
    }

    fun exportMonthlyReportToCsv(
        report: MonthlyReportSummary,
        statement: MonthlyStatementSummary,
        currencySymbol: String = "৳"
    ): String {
        val sb = StringBuilder()
        sb.appendLine("Pocket Monthly Financial Report - ${report.monthLabel}")
        sb.appendLine("Metric,Amount")
        sb.appendLine("Opening Balance,${statement.openingBalance}")
        sb.appendLine("Total Income,${report.totalIncome}")
        sb.appendLine("Total Expenses,${report.totalExpenses}")
        sb.appendLine("Net Savings,${report.netSavings}")
        sb.appendLine("Closing Balance,${statement.closingBalance}")
        sb.appendLine()
        sb.appendLine("Expense Breakdown by Category")
        sb.appendLine("Category,Amount,Share (%),Prev Month,MoM Change (%)")
        report.expenseBreakdown.forEach { item ->
            val momStr = item.momChangePercent?.let { String.format(Locale.ENGLISH, "%.1f%%", it) } ?: "N/A"
            sb.appendLine(
                "${escapeCsv(item.categoryName)},${item.amount},${String.format(Locale.ENGLISH, "%.1f", item.percentage)},${item.previousMonthAmount},${escapeCsv(momStr)}"
            )
        }
        sb.appendLine()
        sb.appendLine("Statement Transactions (${statement.rows.size} entries)")
        sb.appendLine("Date,Description,Wallet,Type,Amount,Running Balance")
        statement.rows.forEach { row ->
            sb.appendLine(
                "${escapeCsv(row.dateFormatted)},${escapeCsv(row.description)},${escapeCsv(row.walletName)},${escapeCsv(row.typeLabel)},${row.signedAmount},${row.runningBalance}"
            )
        }
        return sb.toString()
    }

    fun exportMonthlyReportToPdf(
        report: MonthlyReportSummary,
        statement: MonthlyStatementSummary,
        currencySymbol: String,
        outputStream: OutputStream
    ) {
        val pdf = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = pdf.startPage(pageInfo)
        var canvas = page.canvas

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 18f
            isFakeBoldText = true
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(71, 85, 105)
            textSize = 11f
        }
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 58, 138)
            textSize = 13f
            isFakeBoldText = true
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59)
            textSize = 10f
        }
        val boldBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 10f
            isFakeBoldText = true
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(203, 213, 225)
            strokeWidth = 1f
        }

        var y = 44f
        fun ensureSpace(needed: Float) {
            if (y + needed > pageHeight - 40f) {
                pdf.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = pdf.startPage(pageInfo)
                canvas = page.canvas
                y = 44f
            }
        }

        canvas.drawText("Pocket — Monthly Financial Report", 36f, y, titlePaint)
        y += 18f
        canvas.drawText("Period: ${report.monthLabel} (${statement.walletFilterLabel})", 36f, y, subtitlePaint)
        y += 14f
        canvas.drawLine(36f, y, pageWidth - 36f, y, linePaint)
        y += 20f

        canvas.drawText("Summary", 36f, y, sectionPaint)
        y += 16f
        val summaryItems = listOf(
            "Opening Balance" to AccountingEngine.formatAmount(statement.openingBalance, currencySymbol),
            "Total Income" to AccountingEngine.formatAmount(report.totalIncome, currencySymbol),
            "Total Expenses" to AccountingEngine.formatAmount(report.totalExpenses, currencySymbol),
            "Net Cashflow" to AccountingEngine.formatSignedAmount(report.netSavings, currencySymbol),
            "Closing Balance" to AccountingEngine.formatAmount(statement.closingBalance, currencySymbol)
        )
        summaryItems.forEach { (label, value) ->
            canvas.drawText(label, 42f, y, bodyPaint)
            canvas.drawText(value, 240f, y, boldBodyPaint)
            y += 15f
        }
        y += 8f
        canvas.drawLine(36f, y, pageWidth - 36f, y, linePaint)
        y += 20f

        if (report.expenseBreakdown.isNotEmpty()) {
            ensureSpace(40f)
            canvas.drawText("Expense Breakdown by Category", 36f, y, sectionPaint)
            y += 16f
            canvas.drawText("Category", 42f, y, boldBodyPaint)
            canvas.drawText("Amount", 260f, y, boldBodyPaint)
            canvas.drawText("Share", 380f, y, boldBodyPaint)
            canvas.drawText("vs Last Month", 450f, y, boldBodyPaint)
            y += 14f
            report.expenseBreakdown.forEach { item ->
                ensureSpace(16f)
                val momText = item.momChangePercent?.let {
                    val arrow = if (it >= 0) "↑" else "↓"
                    "$arrow ${String.format(Locale.ENGLISH, "%.0f%%", kotlin.math.abs(it))}"
                } ?: "—"
                canvas.drawText(item.categoryName.take(28), 42f, y, bodyPaint)
                canvas.drawText(AccountingEngine.formatAmount(item.amount, currencySymbol), 260f, y, bodyPaint)
                canvas.drawText("${String.format(Locale.ENGLISH, "%.1f", item.percentage)}%", 380f, y, bodyPaint)
                canvas.drawText(momText, 450f, y, bodyPaint)
                y += 14f
            }
            y += 8f
            canvas.drawLine(36f, y, pageWidth - 36f, y, linePaint)
            y += 20f
        }

        ensureSpace(40f)
        canvas.drawText("Statement Ledger (${statement.rows.size} entries)", 36f, y, sectionPaint)
        y += 16f
        canvas.drawText("Date", 42f, y, boldBodyPaint)
        canvas.drawText("Description", 125f, y, boldBodyPaint)
        canvas.drawText("Wallet", 315f, y, boldBodyPaint)
        canvas.drawText("Amount", 405f, y, boldBodyPaint)
        canvas.drawText("Balance", 485f, y, boldBodyPaint)
        y += 14f

        statement.rows.forEach { row ->
            ensureSpace(16f)
            canvas.drawText(row.dateFormatted.take(14), 42f, y, bodyPaint)
            canvas.drawText(row.description.take(28), 125f, y, bodyPaint)
            canvas.drawText(row.walletName.take(14), 315f, y, bodyPaint)
            canvas.drawText(AccountingEngine.formatSignedAmount(row.signedAmount, currencySymbol), 405f, y, bodyPaint)
            canvas.drawText(AccountingEngine.formatAmount(row.runningBalance, currencySymbol), 485f, y, bodyPaint)
            y += 14f
        }

        pdf.finishPage(page)
        pdf.writeTo(outputStream)
        pdf.close()
    }

    fun exportLedgerToCsv(entries: List<UnifiedLedgerEntry>): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH)
        val sb = StringBuilder()
        sb.appendLine("Date,Type,Description,Category,Wallet,Secondary Wallet,Amount,Fee,Reference,Tags")
        entries.forEach { e ->
            val dt = fmt.format(Date(e.date))
            sb.appendLine(
                "${escapeCsv(dt)},${escapeCsv(e.kind.name)},${escapeCsv(e.title)},${escapeCsv(e.categoryName)},${escapeCsv(e.walletName)},${escapeCsv(e.secondaryWalletName ?: "")},${e.signedAmount},${e.feeAmount},${escapeCsv(e.reference)},${escapeCsv(e.tags)}"
            )
        }
        return sb.toString()
    }

    fun formatPersonKhataStatementText(
        khata: PersonLoanKhataSummary,
        currencySymbol: String = "৳"
    ): String {
        val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
        val todayStr = dateFmt.format(Date())
        val sb = StringBuilder()
        sb.appendLine("📋 *Pocket Loan Khata Statement*")
        sb.appendLine("👤 *Contact:* ${khata.personName}")
        sb.appendLine("📅 *Date:* $todayStr")
        sb.appendLine("────────────────────────")
        sb.appendLine("• Total Lent (Receivable): ${AccountingEngine.formatMoney(khata.totalLentOutstanding, currencySymbol)}")
        sb.appendLine("• Total Borrowed (Payable): ${AccountingEngine.formatMoney(khata.totalBorrowedOutstanding, currencySymbol)}")
        val netLabel = when {
            khata.netReceivableMinusPayable > 0.0 ->
                "Net Receivable (You will get): ${AccountingEngine.formatMoney(khata.netReceivableMinusPayable, currencySymbol)}"
            khata.netReceivableMinusPayable < 0.0 ->
                "Net Payable (You owe): ${AccountingEngine.formatMoney(kotlin.math.abs(khata.netReceivableMinusPayable), currencySymbol)}"
            else -> "Net Position: Settled (${AccountingEngine.formatMoney(0.0, currencySymbol)})"
        }
        sb.appendLine("• *$netLabel*")
        sb.appendLine("────────────────────────")
        khata.loans.sortedBy { it.loan.startDate }.forEachIndexed { idx, summary ->
            val dir = if (summary.loan.direction == LoanDirection.LENT.name) "Lent" else "Borrowed"
            val isSettled = summary.status == "SETTLED" || summary.outstandingPrincipal <= 0.005
            val status = if (isSettled) "Settled" else "Open (Remaining: ${AccountingEngine.formatMoney(summary.outstandingPrincipal, currencySymbol)})"
            val createdStr = dateFmt.format(Date(summary.loan.startDate))
            sb.appendLine("${idx + 1}. *$dir ${AccountingEngine.formatMoney(summary.loan.principalAmount, currencySymbol)}* on $createdStr [$status]")
            if (summary.loan.note.isNotBlank()) {
                sb.appendLine("   Note: ${summary.loan.note}")
            }
            if (summary.loan.dueDate != null) {
                sb.appendLine("   Deadline: ${dateFmt.format(Date(summary.loan.dueDate))}")
            }
            summary.activities.sortedBy { it.date }.forEach { act ->
                val actDate = dateFmt.format(Date(act.date))
                val actType = if (act.activityType == LoanActivityType.DISBURSEMENT.name) {
                    "Initial $dir"
                } else {
                    "Partial/Full Repayment"
                }
                val interestStr = if (act.interestPortion > 0.0) {
                    " (+${AccountingEngine.formatMoney(act.interestPortion, currencySymbol)} interest)"
                } else ""
                val noteSuffix = if (act.note.isNotBlank()) " — ${act.note}" else ""
                sb.appendLine("   • $actDate: $actType ${AccountingEngine.formatMoney(act.principalPortion, currencySymbol)}$interestStr$noteSuffix")
            }
        }
        sb.appendLine("────────────────────────")
        sb.append("Generated via Pocket")
        return sb.toString()
    }

    fun exportPersonKhataStatementToPdf(
        khata: PersonLoanKhataSummary,
        currencySymbol: String,
        outputStream: OutputStream
    ) {
        val pdf = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = pdf.startPage(pageInfo)
        var canvas = page.canvas

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 18f
            isFakeBoldText = true
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(71, 85, 105)
            textSize = 11f
        }
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 58, 138)
            textSize = 13f
            isFakeBoldText = true
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59)
            textSize = 10f
        }
        val boldBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 10f
            isFakeBoldText = true
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(203, 213, 225)
            strokeWidth = 1f
        }

        val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
        var y = 44f
        fun ensureSpace(needed: Float) {
            if (y + needed > pageHeight - 40f) {
                pdf.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = pdf.startPage(pageInfo)
                canvas = page.canvas
                y = 44f
            }
        }

        canvas.drawText("Pocket — Loan Khata Statement", 36f, y, titlePaint)
        y += 18f
        canvas.drawText("Contact: ${khata.personName}   •   Generated: ${dateFmt.format(Date())}", 36f, y, subtitlePaint)
        y += 14f
        canvas.drawLine(36f, y, pageWidth - 36f, y, linePaint)
        y += 20f

        canvas.drawText("Khata Summary", 36f, y, sectionPaint)
        y += 16f
        val netText = when {
            khata.netReceivableMinusPayable > 0.0 ->
                "Receivable (${AccountingEngine.formatAmount(khata.netReceivableMinusPayable, currencySymbol)})"
            khata.netReceivableMinusPayable < 0.0 ->
                "Payable (${AccountingEngine.formatAmount(kotlin.math.abs(khata.netReceivableMinusPayable), currencySymbol)})"
            else -> "Settled (${AccountingEngine.formatAmount(0.0, currencySymbol)})"
        }
        val summaryRows = listOf(
            "Total Lent (Outstanding)" to AccountingEngine.formatAmount(khata.totalLentOutstanding, currencySymbol),
            "Total Borrowed (Outstanding)" to AccountingEngine.formatAmount(khata.totalBorrowedOutstanding, currencySymbol),
            "Net Position" to netText
        )
        summaryRows.forEach { (label, value) ->
            canvas.drawText(label, 42f, y, bodyPaint)
            canvas.drawText(value, 240f, y, boldBodyPaint)
            y += 15f
        }
        y += 8f
        canvas.drawLine(36f, y, pageWidth - 36f, y, linePaint)
        y += 20f

        khata.loans.sortedBy { it.loan.startDate }.forEachIndexed { idx, summary ->
            ensureSpace(56f)
            val dir = if (summary.loan.direction == LoanDirection.LENT.name) "Lent" else "Borrowed"
            val isSettled = summary.status == "SETTLED" || summary.outstandingPrincipal <= 0.005
            val status = if (isSettled) "Settled" else "Remaining: ${AccountingEngine.formatAmount(summary.outstandingPrincipal, currencySymbol)}"
            canvas.drawText(
                "${idx + 1}. $dir ${AccountingEngine.formatAmount(summary.loan.principalAmount, currencySymbol)} (${dateFmt.format(Date(summary.loan.startDate))}) — $status",
                36f,
                y,
                sectionPaint
            )
            y += 15f
            if (summary.loan.note.isNotBlank()) {
                canvas.drawText("Note: ${summary.loan.note}", 44f, y, subtitlePaint)
                y += 14f
            }
            summary.activities.sortedBy { it.date }.forEach { act ->
                ensureSpace(16f)
                val actDate = dateFmt.format(Date(act.date))
                val actLabel = if (act.activityType == LoanActivityType.DISBURSEMENT.name) "Initial $dir" else "Repayment"
                val intLabel = if (act.interestPortion > 0.0) " (+${AccountingEngine.formatAmount(act.interestPortion, currencySymbol)} int.)" else ""
                val notePart = if (act.note.isNotBlank()) " — ${act.note.take(36)}" else ""
                canvas.drawText("• $actDate", 48f, y, bodyPaint)
                canvas.drawText(actLabel, 135f, y, boldBodyPaint)
                canvas.drawText(
                    "${AccountingEngine.formatAmount(act.principalPortion, currencySymbol)}$intLabel$notePart",
                    245f,
                    y,
                    bodyPaint
                )
                y += 14f
            }
            y += 10f
        }

        pdf.finishPage(page)
        pdf.writeTo(outputStream)
        pdf.close()
    }

    private fun escapeCsv(value: String): String {
        val cleaned = value.replace("\"", "\"\"")
        return if (cleaned.contains(",") || cleaned.contains("\n") || cleaned.contains("\"")) {
            "\"$cleaned\""
        } else {
            cleaned
        }
    }

    private fun deriveAesKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, 65536, 256)
        val secret = factory.generateSecret(spec)
        return SecretKeySpec(secret.encoded, "AES")
    }
}
