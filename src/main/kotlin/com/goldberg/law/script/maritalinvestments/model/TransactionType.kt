package com.goldberg.law.script.maritalinvestments.model

enum class TransactionType {
    BUY,
    SELL,
    RE_INVESTMENT,
    DISTRIBUTION,  // money entering that can be distributed based on the shares owned
    DEPOSIT,
    WITHDRAWAL,
    SWEEP_IN,  // when money sitting in the base account and is swept into partner banks or accounts
    SWEEP_OUT;  // when money enters the base account and is swept out of partner banks or accounts
}