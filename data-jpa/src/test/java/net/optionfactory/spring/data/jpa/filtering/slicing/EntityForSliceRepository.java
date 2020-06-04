package net.optionfactory.spring.data.jpa.filtering.slicing;

import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntityForSliceRepository extends JpaRepository<EntityForSlice, Long>, WhitelistFilteringRepository<EntityForSlice> {

    Slice<EntityForSlice> findByName(String name, Pageable pr);

}
