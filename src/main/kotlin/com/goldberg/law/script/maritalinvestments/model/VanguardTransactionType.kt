package com.goldberg.law.script.maritalinvestments.model

enum class VanguardTransactionType(val value: String) {
    BUY("Buy"),
    BUY_EXCHANGE("Buy (exchange)"),  // same as buy
    CAPITAL_GAIN_LT("Capital gain (LT)"),
    CAPITAL_GAIN_ST("Capital gain (ST)"),
    CORP_ACTION_REDEMPTION("Corp Action (Redemption)"),  // same as a sell
    DIVIDEND("Dividend"),  // money entering vanguard
    FEDERAL_WIRE_RETURN("Federal Wire Return"),  // money entering vanguard
    FUNDS_RECEIVED("Funds Received"),  // money entering vanguard
    INTEREST("Interest"),
    REINVESTMENT("Reinvestment"),
    REINVESTMENT_LT_GAIN("Reinvestment (LT gain)"),
    REINVESTMENT_ST_GAIN("Reinvestment (ST gain)"),
    SELL_EXCHANGE("Sell (exchange)"),
    SWEEP_IN("Sweep in"),
    SWEEP_OUT("Sweep out"),
    WIRE_IN("Wire In"),
    WIRE_OUT("Wire Out"),
    WITHDRAWAL("Withdrawal");

    fun toTransactionType() = when(this) {
        BUY, BUY_EXCHANGE -> TransactionType.BUY
        REINVESTMENT, REINVESTMENT_ST_GAIN, REINVESTMENT_LT_GAIN -> TransactionType.RE_INVESTMENT
        DIVIDEND, CAPITAL_GAIN_ST, CAPITAL_GAIN_LT, INTEREST -> TransactionType.DISTRIBUTION
        SELL_EXCHANGE, CORP_ACTION_REDEMPTION -> TransactionType.SELL
        FEDERAL_WIRE_RETURN, FUNDS_RECEIVED, WIRE_IN -> TransactionType.DEPOSIT
        WIRE_OUT, WITHDRAWAL -> TransactionType.WITHDRAWAL
        SWEEP_IN -> TransactionType.SWEEP_IN;
        SWEEP_OUT -> TransactionType.SWEEP_OUT;
    }

    companion object {
        private val BY_VALUE = entries.associateBy { it.value }

        fun fromValue(value: String?): VanguardTransactionType? = if (value == null) null
            else BY_VALUE[value]
    }
}