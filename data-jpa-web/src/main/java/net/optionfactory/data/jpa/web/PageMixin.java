package net.optionfactory.data.jpa.web;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@JsonRootName(value = "page")
public interface PageMixin<T> {

    @JsonProperty("size")
    long getTotalElements();

    @JsonProperty("data")
    List<T> getContent();

    @JsonIgnore
    int getNumber();

    @JsonIgnore
    int getSize();

    @JsonIgnore
    int getNumberOfElements();

    @JsonIgnore
    boolean hasContent();

    @JsonIgnore
    Sort getSort();

    @JsonIgnore
    boolean isFirst();

    @JsonIgnore
    boolean isLast();

    @JsonIgnore
    boolean hasNext();

    @JsonIgnore
    boolean hasPrevious();

    @JsonIgnore
    Pageable getPageable();

    @JsonIgnore
    int getTotalPages();

    @JsonIgnore
    boolean isEmpty();
}
