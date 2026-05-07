UPDATE transactions
SET date = DATE_FORMAT(STR_TO_DATE(date, '%c/%e/%Y'), '%Y-%m-%d')
WHERE date IS NOT NULL;

UPDATE checks
SET date = DATE_FORMAT(STR_TO_DATE(date, '%c/%e/%Y'), '%Y-%m-%d')
WHERE date IS NOT NULL;

UPDATE bank_statements
SET date = DATE_FORMAT(STR_TO_DATE(date, '%c/%e/%Y'), '%Y-%m-%d')
WHERE date IS NOT NULL;
