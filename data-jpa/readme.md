# data-jpa

Define filters directly on `@Entity` types.

## Filter values

- `@BooleanCompare`: `true | false`  or the configured string for boolean values

- `@TextCompare`: `EQUALS | CONTAINS | STARTS_WITH | ENDS_WITH`, `CASE_SENSITIVE | IGNORE_CASE`, _text_

- `@NumberCompare`: `LT | LTE | EQ | GTE | GT`, _number_

- `@LocalDateCompare`: `EQ | LT | GT | LTE | GTE | BETWEEN`, _date_ , _end date_ when the operator is `BETWEEN`

- `@InEnum`: _values..._ list

- `@InList`: _values..._ list which can contain a null value to match null fields too
