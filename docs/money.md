# Money Representation

Auto-Parto stores monetary values as signed Java `long` values in currency minor units. Every value carries an ISO 4217 currency code; no balance, price, or delivery amount is stored as a floating-point value.

`auto.parto.money.Money` is the shared arithmetic boundary. `Money.parse` and `Money.fromMajor` reject excess precision and values outside the signed 64-bit range. Addition, subtraction, and negation use overflow-safe arithmetic, and arithmetic between different currencies is rejected.

Currency precision is defined by `Currency.minorUnitScale()`. The supported set includes two-decimal currencies such as MAD and EUR, zero-decimal currencies such as JPY, KRW, CLP, and VND, and three-decimal currencies such as BHD, JOD, KWD, OMR, and TND.

PostgreSQL wallets, listings, offers, and their ledger amounts use `BIGINT` minor-unit columns. The application validates currency codes against the supported enum before persistence. Non-negative price and balance fields reject negative values; wallet debit operations use a negative signed delta and still enforce a non-negative resulting balance.

Legacy seller and offer request boundaries convert decimal input once at the edge with `Money`; persistence and domain models use minor units. Decimal input is retained only where compatibility with the existing legacy HTTP contract requires it.
