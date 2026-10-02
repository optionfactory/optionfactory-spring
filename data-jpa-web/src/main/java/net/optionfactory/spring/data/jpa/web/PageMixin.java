package net.optionfactory.spring.data.jpa.web;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/// A jackson mixin reducing a serialized [org.springframework.data.domain.Page] to the two
/// properties a paginated client needs: `size`, the total number of elements matching the query
/// across every page, and `data`, the content of the requested page.
///
/// Every other bean property of the page (number, page size, sort, first/last flags, the
/// pageable, ...) is ignored: the client already knows which page it asked for, and the total
/// alone is enough to render a pager.
///
/// ```java
/// final var mapper = JsonMapper.builder()
///         .addMixIn(Page.class, PageMixin.class)
///         .build();
/// ```
///
/// With it, `new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 10)` is written as
/// `{"data":["a","b"],"size":10}`.
///
/// The mapper must also be the one used by the http message converter for the mixin to apply to
/// controller responses. The `page` root name is only used when the mapper wraps root values
/// (`SerializationFeature.WRAP_ROOT_VALUE`), which is off by default.
///
/// @param <T> the type of the page elements
@JsonRootName(value = "page")
public interface PageMixin<T> {

    /// @return the total number of elements across all pages, written as `size`
    @JsonProperty("size")
    long getTotalElements();

    /// @return the elements of the current page, written as `data`
    @JsonProperty("data")
    List<T> getContent();

    /// @return the page number, not serialized
    @JsonIgnore
    int getNumber();

    /// @return the requested page size, not serialized
    @JsonIgnore
    int getSize();

    /// @return the number of elements on this page, not serialized
    @JsonIgnore
    int getNumberOfElements();

    /// @return whether the page has content, not serialized
    @JsonIgnore
    boolean hasContent();

    /// @return the sort, not serialized
    @JsonIgnore
    Sort getSort();

    /// @return whether this is the first page, not serialized
    @JsonIgnore
    boolean isFirst();

    /// @return whether this is the last page, not serialized
    @JsonIgnore
    boolean isLast();

    /// @return whether a next page exists, not serialized
    @JsonIgnore
    boolean hasNext();

    /// @return whether a previous page exists, not serialized
    @JsonIgnore
    boolean hasPrevious();

    /// @return the pageable, not serialized
    @JsonIgnore
    Pageable getPageable();

    /// @return the number of pages, not serialized
    @JsonIgnore
    int getTotalPages();

    /// @return whether the page is empty, not serialized
    @JsonIgnore
    boolean isEmpty();
}
