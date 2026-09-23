package net.optionfactory.spring.data.jpa.filtering.filters;

/// The quantifier a filter applies when its path crosses a collection: whether the row is kept
/// because *some* element matches, or because *no* element does.
///
/// The quantifier and the condition are independent questions, and conflating them is what makes a
/// negated filter over a collection read wrong. "Pets not tagged `x`" is [#NONE] over the condition
/// `label = x`. It is *not* [#ANY] over `label <> x`, which keeps a pet tagged both `x` and `y` —
/// it does have a tag that is not `x` — and drops a pet with no tags at all, which has no tag to
/// satisfy anything. Both readings are expressible, and which one a filter means is a whitelisting
/// decision, made by whoever also names the filter and writes the label the user reads.
///
/// A filter whose path crosses a collection must state its quantifier: every annotation defaults to
/// [#UNSTATED], which is rejected on such a path when the repository is built. Neither reading is a
/// safe guess — `ANY` over a negated operator silently answers a different question than the label
/// on the filter asks — so the choice is made where the filter is whitelisted, and a filter written
/// before quantifiers existed fails at startup instead of returning different rows. A filter whose
/// path crosses no collection needs none: with nothing to quantify over, the condition applies to the
/// row itself, and `UNSTATED` is accepted there.
///
/// ### Composition
///
/// Filters reaching the same collection are folded into one subquery, which describes *one element*:
/// the quantifier then only decides whether such an element must exist. Filters that disagree on the
/// quantifier describe different elements, so they simply get one subquery each — there is nothing to
/// reconcile:
///
/// ```java
/// // byTag=x, byWeight>5     ->  EXISTS (label = x AND weight > 5)      one tag, both conditions
/// // withoutTag=x, withoutWeight>5 -> NOT EXISTS (label = x AND weight > 5)  no tag with both
/// // byTag=x, withoutTag=y   ->  EXISTS (label = x) AND NOT EXISTS (label = y)
/// ```
///
/// @see net.optionfactory.spring.data.jpa.filtering.filters.FilterTraversal
public enum Match {

    /// The row is kept when at least one element satisfies the condition, rendered as `EXISTS`. A row
    /// whose collection is empty is dropped: it has no element to satisfy anything. The reading every
    /// positive operator (`EQ`, `CONTAINS`, `GT`, ...) has always had.
    ANY,
    /// The row is kept when no element satisfies the condition, rendered as `NOT EXISTS`. A row whose
    /// collection is empty therefore matches, correctly so: it has, indeed, no element satisfying the
    /// condition. This is what a filter labelled "without tag `x`" or "not in the HR department"
    /// means, and it is expressed as `NONE` over `= x`, never as `ANY` over `<> x`.
    NONE,
    /// No quantifier was stated: the default of every filter annotation. Accepted on a path crossing no
    /// collection, where there is nothing to quantify; rejected with an `InvalidFilterConfiguration`
    /// when the repository is built on a path crossing one.
    UNSTATED;
}
