package com.example.data

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

data class WalletBalanceSummary(
    val wallet: WalletEntity,
    val openingBalance: Double,
    val totalIncome: Double,
    val totalExpenses: Double, // Includes transfer fees & loan interest paid, net of expense reversals
    val grossExpenses: Double,
    val expenseReversals: Double,
    val transferIn: Double,
    val transferOut: Double,
    val transferFeesPaid: Double,
    val loanPrincipalIn: Double,
    val loanPrincipalOut: Double,
    val adjustmentsNet: Double,
    val currentBalance: Double
) {
    val totalMoneyIn: Double
        get() = totalIncome + expenseReversals + transferIn + loanPrincipalIn

    val totalMoneyOut: Double
        get() = grossExpenses + transferOut + loanPrincipalOut
}

enum class LedgerEntryKind {
    INCOME,
    EXPENSE,
    EXPENSE_REVERSAL,
    TRANSFER,
    LOAN_PRINCIPAL,
    ADJUSTMENT
}

data class UnifiedLedgerEntry(
    val uniqueKey: String,
    val kind: LedgerEntryKind,
    val rawId: Long,
    val date: Long,
    val title: String,
    val subtitle: String,
    val walletId: Long,
    val walletName: String,
    val secondaryWalletId: Long? = null,
    val secondaryWalletName: String? = null,
    val categoryId: Long? = null,
    val categoryName: String = "",
    val iconName: String = "RECEIPT",
    val colorHex: String = "#14532D",
    val signedAmount: Double, // Positive for inflow, negative for outflow (or principal for transfer)
    val feeAmount: Double = 0.0,
    val note: String = "",
    val reference: String = "",
    val tags: String = "",
    val isTransferFeeRow: Boolean = false,
    val isLoanInterestRow: Boolean = false,
    val badgeText: String? = null,
    val attachmentUri: String = ""
)

data class LoanCalculatedSummary(
    val loan: LoanEntity,
    val walletName: String,
    val originalPrincipal: Double,
    val totalPrincipalAdditions: Double,
    val principalRepaid: Double,
    val outstandingPrincipal: Double,
    val totalInterestRecorded: Double,
    val status: String, // ACTIVE, PARTIAL, OVERDUE, SETTLED
    val activities: List<LoanActivityEntity>
)

data class PersonLoanKhataSummary(
    val personName: String,
    val loans: List<LoanCalculatedSummary>,
    val totalLentOutstanding: Double,
    val totalBorrowedOutstanding: Double,
    val netReceivableMinusPayable: Double
)

data class CategoryBreakdownItem(
    val category: CategoryEntity,
    val amount: Double,
    val percentage: Float,
    val transactionCount: Int,
    val previousMonthAmount: Double = 0.0,
    val momChangePercent: Float? = null
) {
    val categoryName: String
        get() = category.name

    val monthlyBudget: Double
        get() = category.monthlyBudget

    val changePercentVsPreviousMonth: Float?
        get() = momChangePercent
}

data class DailySpendingPoint(
    val dayOfMonth: Int,
    val label: String,
    val timestamp: Long,
    val income: Double,
    val expense: Double
)

data class MonthlyComparisonPoint(
    val year: Int,
    val month: Int,
    val shortLabel: String,
    val income: Double,
    val expense: Double,
    val net: Double
)

data class MonthlyReportSummary(
    val year: Int,
    val month: Int, // 0-indexed Calendar month
    val monthLabel: String,
    val totalIncome: Double,
    val totalExpenses: Double,
    val netCashFlow: Double,
    val previousMonthIncome: Double = 0.0,
    val previousMonthExpenses: Double = 0.0,
    val expenseMomChangePercent: Float? = null,
    val expenseByCategory: List<CategoryBreakdownItem>,
    val incomeByCategory: List<CategoryBreakdownItem>,
    val dailyPoints: List<DailySpendingPoint>,
    val sixMonthTrend: List<MonthlyComparisonPoint>,
    val loanReceivableOutstanding: Double,
    val loanPayableOutstanding: Double
) {
    val netSavings: Double
        get() = netCashFlow

    val expenseBreakdown: List<CategoryBreakdownItem>
        get() = expenseByCategory

    val expenseChangePercentVsPreviousMonth: Float?
        get() = expenseMomChangePercent

    val incomeChangePercentVsPreviousMonth: Float?
        get() = if (previousMonthIncome > 0.004) {
            (((totalIncome - previousMonthIncome) / previousMonthIncome) * 100.0).toFloat()
        } else {
            null
        }
}

data class StatementRow(
    val date: Long,
    val dateFormatted: String,
    val description: String,
    val walletName: String,
    val typeLabel: String,
    val signedAmount: Double,
    val runningBalance: Double,
    val isFee: Boolean = false
)

data class MonthlyStatementSummary(
    val periodLabel: String,
    val walletFilterLabel: String,
    val openingBalance: Double,
    val totalIncome: Double,
    val totalExpenses: Double, // Net of reversals, includes transfer fees and loan interest paid
    val transferFeesIncludedInExpenses: Double,
    val transfersIn: Double,
    val transfersOut: Double,
    val loanPrincipalReceived: Double,
    val loanPrincipalRepaid: Double,
    val adjustmentsNet: Double,
    val closingBalance: Double,
    val rows: List<StatementRow>
)

object AccountingEngine {

    fun roundCurrency(value: Double): Double {
        return (value * 100.0).roundToLong() / 100.0
    }

    fun currencySymbolForCode(code: String): String {
        return when (code.trim().uppercase()) {
            "", "BDT", "TK", "TAKA" -> "৳"
            "USD", "US$" -> "$"
            "EUR", "EURO" -> "€"
            "GBP" -> "£"
            "INR" -> "₹"
            "JPY", "CNY" -> "¥"
            else -> code.trim().uppercase()
        }
    }

    fun formatMoney(
        amount: Double,
        symbol: String = "৳",
        showSign: Boolean = false,
        includeDecimals: Boolean = true
    ): String {
        val rounded = roundCurrency(amount)
        val absVal = abs(rounded)
        val pattern = if (includeDecimals) "#,##0.00" else "#,##0.##"
        val formatter = DecimalFormat(pattern)
        val formattedNumber = formatter.format(absVal)
        return when {
            rounded < -0.004 -> "−$symbol $formattedNumber"
            rounded > 0.004 && showSign -> "+$symbol $formattedNumber"
            else -> "$symbol $formattedNumber"
        }
    }

    fun formatAmount(amount: Double, symbol: String = "৳", includeDecimals: Boolean = false): String {
        return formatMoney(amount, symbol, showSign = false, includeDecimals = includeDecimals)
    }

    fun formatSignedAmount(amount: Double, symbol: String = "৳", includeDecimals: Boolean = false): String {
        return formatMoney(amount, symbol, showSign = true, includeDecimals = includeDecimals)
    }

    fun evaluateMathExpression(raw: String): Double? {
        return com.example.ui.components.evaluateMathExpression(raw)
    }

    fun calculateTransferFee(
        principalAmount: Double,
        feeMode: TransferFeeMode,
        fixedFeeInput: Double,
        percentageInput: Double
    ): Double {
        if (principalAmount <= 0.0) return 0.0
        val rawFee = when (feeMode) {
            TransferFeeMode.NONE -> 0.0
            TransferFeeMode.MANUAL -> max(0.0, fixedFeeInput)
            TransferFeeMode.PERCENTAGE -> max(0.0, (principalAmount * max(0.0, percentageInput)) / 100.0)
            TransferFeeMode.FIXED_PLUS_PERCENTAGE -> {
                max(0.0, fixedFeeInput) + max(0.0, (principalAmount * max(0.0, percentageInput)) / 100.0)
            }
        }
        return roundCurrency(rawFee)
    }

    /**
     * Calculates the authoritative wallet balance and breakdown from the ledger up to [upToTimestamp] (inclusive).
     */
    fun calculateWalletSummary(
        wallet: WalletEntity,
        transactions: List<TransactionEntity>,
        transfers: List<TransferEntity>,
        loans: List<LoanEntity>,
        loanActivities: List<LoanActivityEntity>,
        adjustments: List<BalanceAdjustmentEntity>,
        upToTimestamp: Long = Long.MAX_VALUE
    ): WalletBalanceSummary {
        val loanMap = loans.associateBy { it.id }

        var income = 0.0
        var grossExpense = 0.0
        var expenseReversal = 0.0
        var transferFees = 0.0

        transactions.forEach { tx ->
            if (tx.walletId == wallet.id && tx.date <= upToTimestamp) {
                when (tx.type) {
                    TransactionType.INCOME.name -> income += tx.amount
                    TransactionType.EXPENSE.name -> {
                        grossExpense += tx.amount
                        if (tx.transferId != null) {
                            transferFees += tx.amount
                        }
                    }
                    TransactionType.EXPENSE_REVERSAL.name -> expenseReversal += tx.amount
                }
            }
        }

        var transferIn = 0.0
        var transferOut = 0.0
        transfers.forEach { tr ->
            if (tr.date <= upToTimestamp) {
                if (tr.destinationWalletId == wallet.id) {
                    transferIn += if (tr.destinationAmount > 0.0) tr.destinationAmount else tr.principalAmount
                }
                if (tr.sourceWalletId == wallet.id) {
                    transferOut += tr.principalAmount
                }
            }
        }

        var loanPrincipalIn = 0.0
        var loanPrincipalOut = 0.0
        loanActivities.forEach { act ->
            if (act.walletId == wallet.id && act.date <= upToTimestamp) {
                val loan = loanMap[act.loanId]
                if (loan != null) {
                    val isLent = loan.direction == LoanDirection.LENT.name
                    when (act.activityType) {
                        LoanActivityType.DISBURSEMENT.name, LoanActivityType.ADJUSTMENT.name -> {
                            if (isLent) {
                                // Lending money reduces wallet balance
                                loanPrincipalOut += act.principalPortion
                            } else {
                                // Borrowing money increases wallet balance
                                loanPrincipalIn += act.principalPortion
                            }
                        }
                        LoanActivityType.REPAYMENT.name -> {
                            if (isLent) {
                                // Receiving principal back increases wallet balance
                                loanPrincipalIn += act.principalPortion
                            } else {
                                // Repaying borrowed principal reduces wallet balance
                                loanPrincipalOut += act.principalPortion
                            }
                        }
                    }
                }
            }
        }

        var adjustmentsNet = 0.0
        adjustments.forEach { adj ->
            if (adj.walletId == wallet.id && !adj.isReversed && adj.date <= upToTimestamp) {
                adjustmentsNet += adj.difference
            }
        }

        val netExpenses = grossExpense - expenseReversal
        val closingBalance = roundCurrency(
            wallet.openingBalance +
                income -
                netExpenses +
                transferIn -
                transferOut +
                loanPrincipalIn -
                loanPrincipalOut +
                adjustmentsNet
        )

        return WalletBalanceSummary(
            wallet = wallet,
            openingBalance = roundCurrency(wallet.openingBalance),
            totalIncome = roundCurrency(income),
            totalExpenses = roundCurrency(netExpenses),
            grossExpenses = roundCurrency(grossExpense),
            expenseReversals = roundCurrency(expenseReversal),
            transferIn = roundCurrency(transferIn),
            transferOut = roundCurrency(transferOut),
            transferFeesPaid = roundCurrency(transferFees),
            loanPrincipalIn = roundCurrency(loanPrincipalIn),
            loanPrincipalOut = roundCurrency(loanPrincipalOut),
            adjustmentsNet = roundCurrency(adjustmentsNet),
            currentBalance = closingBalance
        )
    }

    /**
     * Computes loan outstanding principal and status strictly from the loan activity ledger.
     */
    fun calculateLoanSummaries(
        loans: List<LoanEntity>,
        loanActivities: List<LoanActivityEntity>,
        wallets: List<WalletEntity>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<LoanCalculatedSummary> {
        val walletMap = wallets.associateBy { it.id }
        val activitiesByLoan = loanActivities.groupBy { it.loanId }

        return loans.map { loan ->
            val acts = (activitiesByLoan[loan.id] ?: emptyList()).sortedByDescending { it.date }
            var totalDisbursed = 0.0
            var totalAdditions = 0.0
            var totalRepaid = 0.0
            var totalInterest = 0.0

            acts.forEach { act ->
                when (act.activityType) {
                    LoanActivityType.DISBURSEMENT.name -> totalDisbursed += act.principalPortion
                    LoanActivityType.ADJUSTMENT.name -> totalAdditions += act.principalPortion
                    LoanActivityType.REPAYMENT.name -> {
                        totalRepaid += act.principalPortion
                        totalInterest += act.interestPortion
                    }
                }
            }

            // Fallback if no disbursement row exists yet
            val basePrincipal = if (totalDisbursed > 0.0) totalDisbursed else loan.principalAmount
            val outstanding = roundCurrency(max(0.0, basePrincipal + totalAdditions - totalRepaid))

            val status = when {
                outstanding <= 0.005 -> "SETTLED"
                loan.dueDate != null && loan.dueDate < nowMillis -> "OVERDUE"
                totalRepaid > 0.005 -> "PARTIAL"
                else -> "ACTIVE"
            }

            LoanCalculatedSummary(
                loan = loan,
                walletName = walletMap[loan.initialWalletId]?.name ?: "Wallet",
                originalPrincipal = roundCurrency(basePrincipal),
                totalPrincipalAdditions = roundCurrency(totalAdditions),
                principalRepaid = roundCurrency(totalRepaid),
                outstandingPrincipal = outstanding,
                totalInterestRecorded = roundCurrency(totalInterest),
                status = status,
                activities = acts
            )
        }
    }

    /**
     * Builds a unified, chronological ledger across transactions, transfers, loan principal movements,
     * and balance adjustments.
     */
    fun buildUnifiedLedger(
        wallets: List<WalletEntity>,
        categories: List<CategoryEntity>,
        transactions: List<TransactionEntity>,
        transfers: List<TransferEntity>,
        loans: List<LoanEntity>,
        loanActivities: List<LoanActivityEntity>,
        adjustments: List<BalanceAdjustmentEntity>
    ): List<UnifiedLedgerEntry> {
        val walletMap = wallets.associateBy { it.id }
        val categoryMap = categories.associateBy { it.id }
        val loanMap = loans.associateBy { it.id }
        val result = mutableListOf<UnifiedLedgerEntry>()

        // 1. Standard Income, Expenses (including transfer fee & loan interest expenses), and Expense Reversals
        transactions.forEach { tx ->
            val walletName = walletMap[tx.walletId]?.name ?: "Wallet"
            val cat = categoryMap[tx.categoryId]
            val catName = cat?.name ?: "Uncategorized"
            val isIncome = tx.type == TransactionType.INCOME.name
            val isReversal = tx.type == TransactionType.EXPENSE_REVERSAL.name
            val signed = if (isIncome || isReversal) tx.amount else -tx.amount

            val kind = when (tx.type) {
                TransactionType.INCOME.name -> LedgerEntryKind.INCOME
                TransactionType.EXPENSE_REVERSAL.name -> LedgerEntryKind.EXPENSE_REVERSAL
                else -> LedgerEntryKind.EXPENSE
            }

            val badge = when {
                tx.transferId != null -> "Transfer Fee"
                tx.loanActivityId != null -> "Loan Interest"
                isReversal -> "Refund Reversal"
                else -> null
            }

            result.add(
                UnifiedLedgerEntry(
                    uniqueKey = "TX_${tx.id}",
                    kind = kind,
                    rawId = tx.id,
                    date = tx.date,
                    title = tx.note.ifBlank { catName },
                    subtitle = "$catName · $walletName",
                    walletId = tx.walletId,
                    walletName = walletName,
                    categoryId = tx.categoryId,
                    categoryName = catName,
                    iconName = cat?.iconName ?: if (isIncome) "TRENDING_UP" else "RECEIPT",
                    colorHex = cat?.colorHex ?: if (isIncome) "#15803D" else "#B91C1C",
                    signedAmount = roundCurrency(signed),
                    note = tx.note,
                    reference = tx.reference,
                    tags = tx.tags,
                    isTransferFeeRow = tx.transferId != null,
                    isLoanInterestRow = tx.loanActivityId != null,
                    badgeText = badge,
                    attachmentUri = tx.attachmentUri
                )
            )
        }

        // 2. Transfers (Principal movement)
        transfers.forEach { tr ->
            val srcWallet = walletMap[tr.sourceWalletId]
            val dstWallet = walletMap[tr.destinationWalletId]
            val srcName = srcWallet?.name ?: "Source"
            val dstName = dstWallet?.name ?: "Destination"
            val srcCurr = srcWallet?.currency ?: "BDT"
            val dstCurr = dstWallet?.currency ?: "BDT"
            val isCrossCurrency = !srcCurr.equals(dstCurr, ignoreCase = true) && tr.destinationAmount > 0.0
            val dstSym = currencySymbolForCode(dstCurr)
            val transferSubtitle = if (isCrossCurrency) {
                "Transfer · $srcName to $dstName (+${formatMoney(tr.destinationAmount, dstSym)})"
            } else {
                "Transfer · $srcName to $dstName"
            }
            result.add(
                UnifiedLedgerEntry(
                    uniqueKey = "TR_${tr.id}",
                    kind = LedgerEntryKind.TRANSFER,
                    rawId = tr.id,
                    date = tr.date,
                    title = tr.note.ifBlank { "$srcName → $dstName" },
                    subtitle = transferSubtitle,
                    walletId = tr.sourceWalletId,
                    walletName = srcName,
                    secondaryWalletId = tr.destinationWalletId,
                    secondaryWalletName = dstName,
                    categoryName = "Transfer",
                    iconName = "SWAP_HORIZ",
                    colorHex = "#1D4ED8",
                    signedAmount = roundCurrency(tr.principalAmount),
                    feeAmount = roundCurrency(tr.feeAmount),
                    note = tr.note,
                    badgeText = if (tr.feeAmount > 0) "Fee: ${roundCurrency(tr.feeAmount)}" else "Transfer"
                )
            )
        }

        // 3. Loan Principal Movements
        loanActivities.forEach { act ->
            val loan = loanMap[act.loanId] ?: return@forEach
            if (act.principalPortion <= 0.0) return@forEach
            val walletName = walletMap[act.walletId]?.name ?: "Wallet"
            val isLent = loan.direction == LoanDirection.LENT.name

            val isInflow = when (act.activityType) {
                LoanActivityType.DISBURSEMENT.name, LoanActivityType.ADJUSTMENT.name -> !isLent
                LoanActivityType.REPAYMENT.name -> isLent
                else -> false
            }
            val signed = if (isInflow) act.principalPortion else -act.principalPortion
            val typeLabel = when (act.activityType) {
                LoanActivityType.DISBURSEMENT.name -> if (isLent) "Lent to ${loan.personName}" else "Borrowed from ${loan.personName}"
                LoanActivityType.REPAYMENT.name -> if (isLent) "Repayment from ${loan.personName}" else "Repaid to ${loan.personName}"
                else -> "Loan adjustment (${loan.personName})"
            }

            result.add(
                UnifiedLedgerEntry(
                    uniqueKey = "LA_${act.id}",
                    kind = LedgerEntryKind.LOAN_PRINCIPAL,
                    rawId = act.id,
                    date = act.date,
                    title = act.note.ifBlank { typeLabel },
                    subtitle = "$typeLabel · $walletName",
                    walletId = act.walletId,
                    walletName = walletName,
                    categoryName = "Loan Principal",
                    iconName = "HANDSHAKE",
                    colorHex = "#B45309",
                    signedAmount = roundCurrency(signed),
                    note = act.note,
                    badgeText = if (isLent) "Loan (Receivable)" else "Loan (Payable)",
                    attachmentUri = loan.attachmentUri
                )
            )
        }

        // 4. Balance Adjustments
        adjustments.filter { !it.isReversed }.forEach { adj ->
            val walletName = walletMap[adj.walletId]?.name ?: "Wallet"
            result.add(
                UnifiedLedgerEntry(
                    uniqueKey = "ADJ_${adj.id}",
                    kind = LedgerEntryKind.ADJUSTMENT,
                    rawId = adj.id,
                    date = adj.date,
                    title = adj.note.ifBlank { "Balance Adjustment (${adj.reason})" },
                    subtitle = "${adj.reason} · $walletName",
                    walletId = adj.walletId,
                    walletName = walletName,
                    categoryName = "Reconciliation",
                    iconName = "TUNE",
                    colorHex = "#6D28D9",
                    signedAmount = roundCurrency(adj.difference),
                    note = adj.note,
                    badgeText = "Adjustment"
                )
            )
        }

        return result.sortedWith(compareByDescending<UnifiedLedgerEntry> { it.date }.thenByDescending { it.rawId })
    }

    /**
     * Computes monthly report metrics strictly following the PRD accounting rules:
     * - Excludes transfer principal and loan principal from income and expenses.
     * - Includes transfer fees and loan interest paid in expenses.
     * - Includes loan interest received in income.
     * - Subtracts expense reversals (refunds) from expenses.
     * - Sums only BDT wallets so non-BDT currencies are isolated from BDT reports.
     */
    fun calculateMonthlyReport(
        year: Int,
        month: Int,
        customStartMillis: Long? = null,
        customEndMillis: Long? = null,
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        loans: List<LoanEntity>,
        loanActivities: List<LoanActivityEntity>,
        wallets: List<WalletEntity>,
        reportCurrency: String = "BDT"
    ): MonthlyReportSummary {
        val (periodStart, periodEnd) = if (customStartMillis != null && customEndMillis != null) {
            customStartMillis to customEndMillis
        } else {
            getMonthBounds(year, month)
        }

        val targetCurr = reportCurrency.trim().ifBlank { "BDT" }.uppercase(Locale.ENGLISH)
        val bdtWalletIds = if (wallets.isEmpty()) {
            transactions.map { it.walletId }.toSet()
        } else {
            wallets.filter {
                val c = it.currency.trim().ifBlank { "BDT" }.uppercase(Locale.ENGLISH)
                c == targetCurr
            }
                .map { it.id }
                .toSet()
        }
        val bdtTransactions = transactions.filter { it.walletId in bdtWalletIds }

        val catMap = categories.associateBy { it.id }
        val periodTxs = bdtTransactions.filter { it.date in periodStart..periodEnd }

        var totalIncome = 0.0
        var grossExpense = 0.0
        var totalReversal = 0.0

        val expenseByCatMap = mutableMapOf<Long, Double>()
        val expenseCountMap = mutableMapOf<Long, Int>()
        val incomeByCatMap = mutableMapOf<Long, Double>()
        val incomeCountMap = mutableMapOf<Long, Int>()

        periodTxs.forEach { tx ->
            when (tx.type) {
                TransactionType.INCOME.name -> {
                    totalIncome += tx.amount
                    incomeByCatMap[tx.categoryId] = (incomeByCatMap[tx.categoryId] ?: 0.0) + tx.amount
                    incomeCountMap[tx.categoryId] = (incomeCountMap[tx.categoryId] ?: 0) + 1
                }
                TransactionType.EXPENSE.name -> {
                    grossExpense += tx.amount
                    expenseByCatMap[tx.categoryId] = (expenseByCatMap[tx.categoryId] ?: 0.0) + tx.amount
                    expenseCountMap[tx.categoryId] = (expenseCountMap[tx.categoryId] ?: 0) + 1
                }
                TransactionType.EXPENSE_REVERSAL.name -> {
                    totalReversal += tx.amount
                    expenseByCatMap[tx.categoryId] = (expenseByCatMap[tx.categoryId] ?: 0.0) - tx.amount
                }
            }
        }

        val netExpenses = max(0.0, grossExpense - totalReversal)
        val positiveExpenseTotal = expenseByCatMap.values.filter { it > 0.0 }.sum()

        // Previous Month bounds & totals for Month-over-Month (MoM) comparison
        val prevCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, -1)
        }
        val (prevStart, prevEnd) = getMonthBounds(prevCal.get(Calendar.YEAR), prevCal.get(Calendar.MONTH))
        val prevTxs = bdtTransactions.filter { it.date in prevStart..prevEnd }
        var prevIncome = 0.0
        var prevGrossExpense = 0.0
        var prevReversal = 0.0
        val prevExpenseByCatMap = mutableMapOf<Long, Double>()
        val prevIncomeByCatMap = mutableMapOf<Long, Double>()

        prevTxs.forEach { tx ->
            when (tx.type) {
                TransactionType.INCOME.name -> {
                    prevIncome += tx.amount
                    prevIncomeByCatMap[tx.categoryId] = (prevIncomeByCatMap[tx.categoryId] ?: 0.0) + tx.amount
                }
                TransactionType.EXPENSE.name -> {
                    prevGrossExpense += tx.amount
                    prevExpenseByCatMap[tx.categoryId] = (prevExpenseByCatMap[tx.categoryId] ?: 0.0) + tx.amount
                }
                TransactionType.EXPENSE_REVERSAL.name -> {
                    prevReversal += tx.amount
                    prevExpenseByCatMap[tx.categoryId] = (prevExpenseByCatMap[tx.categoryId] ?: 0.0) - tx.amount
                }
            }
        }
        val prevNetExpenses = max(0.0, prevGrossExpense - prevReversal)
        val overallExpenseMom = if (prevNetExpenses > 0.005) {
            (((netExpenses - prevNetExpenses) / prevNetExpenses) * 100.0).toFloat()
        } else null

        val expenseBreakdown = expenseByCatMap.entries
            .filter { it.value > 0.005 }
            .sortedByDescending { it.value }
            .mapNotNull { (catId, amt) ->
                val cat = catMap[catId] ?: return@mapNotNull null
                val pct = if (positiveExpenseTotal > 0.0) ((amt / positiveExpenseTotal) * 100.0).toFloat() else 0f
                val prevAmt = max(0.0, prevExpenseByCatMap[catId] ?: 0.0)
                val momPct = if (prevAmt > 0.005) {
                    (((amt - prevAmt) / prevAmt) * 100.0).toFloat()
                } else null
                CategoryBreakdownItem(
                    category = cat,
                    amount = roundCurrency(amt),
                    percentage = pct,
                    transactionCount = expenseCountMap[catId] ?: 1,
                    previousMonthAmount = roundCurrency(prevAmt),
                    momChangePercent = momPct
                )
            }

        val incomeBreakdown = incomeByCatMap.entries
            .filter { it.value > 0.005 }
            .sortedByDescending { it.value }
            .mapNotNull { (catId, amt) ->
                val cat = catMap[catId] ?: return@mapNotNull null
                val pct = if (totalIncome > 0.0) ((amt / totalIncome) * 100.0).toFloat() else 0f
                val prevAmt = max(0.0, prevIncomeByCatMap[catId] ?: 0.0)
                val momPct = if (prevAmt > 0.005) {
                    (((amt - prevAmt) / prevAmt) * 100.0).toFloat()
                } else null
                CategoryBreakdownItem(
                    category = cat,
                    amount = roundCurrency(amt),
                    percentage = pct,
                    transactionCount = incomeCountMap[catId] ?: 1,
                    previousMonthAmount = roundCurrency(prevAmt),
                    momChangePercent = momPct
                )
            }

        // Daily points for the selected month
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val dailyIncome = DoubleArray(daysInMonth + 1)
        val dailyExpense = DoubleArray(daysInMonth + 1)

        val txCal = Calendar.getInstance()
        periodTxs.forEach { tx ->
            txCal.timeInMillis = tx.date
            if (txCal.get(Calendar.YEAR) == year && txCal.get(Calendar.MONTH) == month) {
                val d = txCal.get(Calendar.DAY_OF_MONTH)
                when (tx.type) {
                    TransactionType.INCOME.name -> dailyIncome[d] += tx.amount
                    TransactionType.EXPENSE.name -> dailyExpense[d] += tx.amount
                    TransactionType.EXPENSE_REVERSAL.name -> dailyExpense[d] = max(0.0, dailyExpense[d] - tx.amount)
                }
            }
        }

        val monthShort = SimpleDateFormat("MMM", Locale.ENGLISH).format(cal.time)
        val dailyPoints = (1..daysInMonth).map { day ->
            cal.set(Calendar.DAY_OF_MONTH, day)
            DailySpendingPoint(
                dayOfMonth = day,
                label = "$monthShort $day",
                timestamp = cal.timeInMillis,
                income = roundCurrency(dailyIncome[day]),
                expense = roundCurrency(dailyExpense[day])
            )
        }

        // 6-month trend ending at (year, month)
        val sixMonthTrend = (-5..0).map { offset ->
            val c = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, 1)
                add(Calendar.MONTH, offset)
            }
            val y = c.get(Calendar.YEAR)
            val m = c.get(Calendar.MONTH)
            val (s, e) = getMonthBounds(y, m)
            var inc = 0.0
            var exp = 0.0
            bdtTransactions.forEach { tx ->
                if (tx.date in s..e) {
                    when (tx.type) {
                        TransactionType.INCOME.name -> inc += tx.amount
                        TransactionType.EXPENSE.name -> exp += tx.amount
                        TransactionType.EXPENSE_REVERSAL.name -> exp -= tx.amount
                    }
                }
            }
            val cleanExp = max(0.0, exp)
            MonthlyComparisonPoint(
                year = y,
                month = m,
                shortLabel = SimpleDateFormat("MMM", Locale.ENGLISH).format(c.time),
                income = roundCurrency(inc),
                expense = roundCurrency(cleanExp),
                net = roundCurrency(inc - cleanExp)
            )
        }

        val bdtLoans = if (wallets.isEmpty()) loans else loans.filter { it.initialWalletId in bdtWalletIds }
        val loanSummaries = calculateLoanSummaries(bdtLoans, loanActivities, wallets)
        val receivable = loanSummaries
            .filter { it.loan.direction == LoanDirection.LENT.name }
            .sumOf { it.outstandingPrincipal }
        val payable = loanSummaries
            .filter { it.loan.direction == LoanDirection.BORROWED.name }
            .sumOf { it.outstandingPrincipal }

        val monthLabel = if (customStartMillis != null && customEndMillis != null) {
            val fmt = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
            "${fmt.format(Date(customStartMillis))} – ${fmt.format(Date(customEndMillis))}"
        } else {
            val c = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, 1)
            }
            SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(c.time)
        }

        return MonthlyReportSummary(
            year = year,
            month = month,
            monthLabel = monthLabel,
            totalIncome = roundCurrency(totalIncome),
            totalExpenses = roundCurrency(netExpenses),
            netCashFlow = roundCurrency(totalIncome - netExpenses),
            previousMonthIncome = roundCurrency(prevIncome),
            previousMonthExpenses = roundCurrency(prevNetExpenses),
            expenseMomChangePercent = overallExpenseMom,
            expenseByCategory = expenseBreakdown,
            incomeByCategory = incomeBreakdown,
            dailyPoints = dailyPoints,
            sixMonthTrend = sixMonthTrend,
            loanReceivableOutstanding = roundCurrency(receivable),
            loanPayableOutstanding = roundCurrency(payable)
        )
    }

    /**
     * Computes chronological running balances after each movement for a single wallet (Bank Statement / Mini Ledger).
     * Returns entries sorted newest-first paired with the wallet's running balance immediately after that entry.
     */
    fun buildWalletRunningBalanceLedger(
        wallet: WalletEntity,
        unifiedLedger: List<UnifiedLedgerEntry>,
        transfers: List<TransferEntity>
    ): List<Pair<UnifiedLedgerEntry, Double>> {
        val transferMap = transfers.associateBy { it.id }
        val walletEntriesChronological = unifiedLedger
            .filter { it.walletId == wallet.id || it.secondaryWalletId == wallet.id }
            .sortedWith(compareBy<UnifiedLedgerEntry> { it.date }.thenBy { it.rawId })

        var running = roundCurrency(wallet.openingBalance)
        val withRunning = walletEntriesChronological.map { entry ->
            val deltaForWallet = when (entry.kind) {
                LedgerEntryKind.TRANSFER -> {
                    val tr = transferMap[entry.rawId]
                    if (entry.walletId == wallet.id) {
                        // Source wallet of transfer (note: fee is already a separate EXPENSE row in unifiedLedger)
                        -entry.signedAmount
                    } else {
                        // Destination wallet of transfer
                        if (tr != null && tr.destinationAmount > 0.0) tr.destinationAmount else entry.signedAmount
                    }
                }
                else -> entry.signedAmount
            }
            running = roundCurrency(running + deltaForWallet)
            entry to running
        }
        return withRunning.asReversed()
    }

    /**
     * Generates a complete Monthly Statement for all wallets or a specific wallet, proving:
     * Opening Balance + Income - Expenses + Transfers In - Transfers Out + Loan Principal In - Loan Principal Out + Adjustments = Closing Balance
     */
    fun generateMonthlyStatement(
        year: Int,
        month: Int,
        walletFilterId: Long?, // null = All Wallets Combined (BDT)
        wallets: List<WalletEntity>,
        categories: List<CategoryEntity>,
        transactions: List<TransactionEntity>,
        transfers: List<TransferEntity>,
        loans: List<LoanEntity>,
        loanActivities: List<LoanActivityEntity>,
        adjustments: List<BalanceAdjustmentEntity>
    ): MonthlyStatementSummary {
        val (periodStart, periodEnd) = getMonthBounds(year, month)
        val targetWallets = if (walletFilterId != null) {
            wallets.filter { it.id == walletFilterId }
        } else {
            wallets.filter { it.currency.isBlank() || it.currency.equals("BDT", ignoreCase = true) }
        }
        val targetWalletIds = targetWallets.map { it.id }.toSet()
        val walletMap = wallets.associateBy { it.id }
        val catMap = categories.associateBy { it.id }
        val loanMap = loans.associateBy { it.id }

        // 1. Opening Balance = wallet balance as of (periodStart - 1)
        val openingBalance = targetWallets.sumOf { w ->
            calculateWalletSummary(
                wallet = w,
                transactions = transactions,
                transfers = transfers,
                loans = loans,
                loanActivities = loanActivities,
                adjustments = adjustments,
                upToTimestamp = periodStart - 1
            ).currentBalance
        }

        val dateFmt = SimpleDateFormat("MMM dd", Locale.ENGLISH)
        val periodStartFormatted = dateFmt.format(Date(periodStart))

        var totalIncome = 0.0
        var totalExpenses = 0.0
        var transferFees = 0.0
        var transfersIn = 0.0
        var transfersOut = 0.0
        var loanPrincipalReceived = 0.0
        var loanPrincipalRepaid = 0.0
        var adjustmentsNet = 0.0

        data class RawMovement(
            val date: Long,
            val order: Int,
            val description: String,
            val walletName: String,
            val typeLabel: String,
            val signedAmount: Double,
            val isFee: Boolean = false
        )

        val movements = mutableListOf<RawMovement>()

        // Transactions in period
        transactions.filter { it.date in periodStart..periodEnd && it.walletId in targetWalletIds }.forEach { tx ->
            val wName = walletMap[tx.walletId]?.name ?: "Wallet"
            val catName = catMap[tx.categoryId]?.name ?: "Category"
            when (tx.type) {
                TransactionType.INCOME.name -> {
                    totalIncome += tx.amount
                    movements.add(
                        RawMovement(
                            date = tx.date,
                            order = 1,
                            description = tx.note.ifBlank { catName },
                            walletName = wName,
                            typeLabel = if (tx.loanActivityId != null) "Loan Interest In" else "Income",
                            signedAmount = tx.amount
                        )
                    )
                }
                TransactionType.EXPENSE.name -> {
                    totalExpenses += tx.amount
                    val isFee = tx.transferId != null
                    if (isFee) transferFees += tx.amount
                    movements.add(
                        RawMovement(
                            date = tx.date,
                            order = 2,
                            description = tx.note.ifBlank { catName },
                            walletName = wName,
                            typeLabel = when {
                                isFee -> "Transfer fee"
                                tx.loanActivityId != null -> "Loan Interest Out"
                                else -> "Expense"
                            },
                            signedAmount = -tx.amount,
                            isFee = isFee
                        )
                    )
                }
                TransactionType.EXPENSE_REVERSAL.name -> {
                    totalExpenses -= tx.amount
                    movements.add(
                        RawMovement(
                            date = tx.date,
                            order = 2,
                            description = tx.note.ifBlank { "Refund ($catName)" },
                            walletName = wName,
                            typeLabel = "Expense Reversal",
                            signedAmount = tx.amount
                        )
                    )
                }
            }
        }

        // Transfers in period
        transfers.filter { it.date in periodStart..periodEnd }.forEach { tr ->
            val srcName = walletMap[tr.sourceWalletId]?.name ?: "Source"
            val dstName = walletMap[tr.destinationWalletId]?.name ?: "Destination"
            val desc = tr.note.ifBlank { "$srcName to $dstName" }
            if (tr.sourceWalletId in targetWalletIds) {
                transfersOut += tr.principalAmount
                movements.add(
                    RawMovement(
                        date = tr.date,
                        order = 3,
                        description = desc,
                        walletName = srcName,
                        typeLabel = "Transfer out",
                        signedAmount = -tr.principalAmount
                    )
                )
            }
            if (tr.destinationWalletId in targetWalletIds) {
                val creditedAmount = if (tr.destinationAmount > 0.0) tr.destinationAmount else tr.principalAmount
                transfersIn += creditedAmount
                movements.add(
                    RawMovement(
                        date = tr.date,
                        order = 4,
                        description = desc,
                        walletName = dstName,
                        typeLabel = "Transfer in",
                        signedAmount = creditedAmount
                    )
                )
            }
        }

        // Loan principal activities in period
        loanActivities.filter { it.date in periodStart..periodEnd && it.walletId in targetWalletIds }.forEach { act ->
            if (act.principalPortion <= 0.0) return@forEach
            val loan = loanMap[act.loanId] ?: return@forEach
            val wName = walletMap[act.walletId]?.name ?: "Wallet"
            val isLent = loan.direction == LoanDirection.LENT.name
            val isInflow = when (act.activityType) {
                LoanActivityType.DISBURSEMENT.name, LoanActivityType.ADJUSTMENT.name -> !isLent
                LoanActivityType.REPAYMENT.name -> isLent
                else -> false
            }

            if (isInflow) {
                loanPrincipalReceived += act.principalPortion
                movements.add(
                    RawMovement(
                        date = act.date,
                        order = 5,
                        description = act.note.ifBlank {
                            if (isLent) "Principal back from ${loan.personName}" else "Borrowed from ${loan.personName}"
                        },
                        walletName = wName,
                        typeLabel = "Loan principal in",
                        signedAmount = act.principalPortion
                    )
                )
            } else {
                loanPrincipalRepaid += act.principalPortion
                movements.add(
                    RawMovement(
                        date = act.date,
                        order = 6,
                        description = act.note.ifBlank {
                            if (isLent) "Lent to ${loan.personName}" else "Repaid to ${loan.personName}"
                        },
                        walletName = wName,
                        typeLabel = "Loan principal out",
                        signedAmount = -act.principalPortion
                    )
                )
            }
        }

        // Balance adjustments in period
        adjustments.filter {
            !it.isReversed && it.date in periodStart..periodEnd && it.walletId in targetWalletIds
        }.forEach { adj ->
            val wName = walletMap[adj.walletId]?.name ?: "Wallet"
            adjustmentsNet += adj.difference
            movements.add(
                RawMovement(
                    date = adj.date,
                    order = 7,
                    description = adj.note.ifBlank { "Adjustment (${adj.reason})" },
                    walletName = wName,
                    typeLabel = "Adjustment",
                    signedAmount = adj.difference
                )
            )
        }

        val sortedMovements = movements.sortedWith(compareBy<RawMovement> { it.date }.thenBy { it.order })
        val rows = mutableListOf<StatementRow>()
        var running = roundCurrency(openingBalance)

        rows.add(
            StatementRow(
                date = periodStart,
                dateFormatted = periodStartFormatted,
                description = "Opening balance",
                walletName = if (walletFilterId == null) "All Wallets" else (walletMap[walletFilterId]?.name ?: "Wallet"),
                typeLabel = "Opening",
                signedAmount = roundCurrency(openingBalance),
                runningBalance = running
            )
        )

        sortedMovements.forEach { m ->
            running = roundCurrency(running + m.signedAmount)
            rows.add(
                StatementRow(
                    date = m.date,
                    dateFormatted = dateFmt.format(Date(m.date)),
                    description = m.description,
                    walletName = m.walletName,
                    typeLabel = m.typeLabel,
                    signedAmount = roundCurrency(m.signedAmount),
                    runningBalance = running,
                    isFee = m.isFee
                )
            )
        }

        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val periodLabel = SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(cal.time)
        val walletFilterLabel = if (walletFilterId == null) {
            "All Wallets Combined"
        } else {
            walletMap[walletFilterId]?.name ?: "Selected Wallet"
        }

        return MonthlyStatementSummary(
            periodLabel = periodLabel,
            walletFilterLabel = walletFilterLabel,
            openingBalance = roundCurrency(openingBalance),
            totalIncome = roundCurrency(totalIncome),
            totalExpenses = roundCurrency(totalExpenses),
            transferFeesIncludedInExpenses = roundCurrency(transferFees),
            transfersIn = roundCurrency(transfersIn),
            transfersOut = roundCurrency(transfersOut),
            loanPrincipalReceived = roundCurrency(loanPrincipalReceived),
            loanPrincipalRepaid = roundCurrency(loanPrincipalRepaid),
            adjustmentsNet = roundCurrency(adjustmentsNet),
            closingBalance = running,
            rows = rows
        )
    }

    fun getMonthBounds(year: Int, month: Int): Pair<Long, Long> {
        val startCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, startCal.getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return startCal.timeInMillis to endCal.timeInMillis
    }
}
