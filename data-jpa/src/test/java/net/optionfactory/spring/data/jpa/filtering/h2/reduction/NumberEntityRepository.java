package net.optionfactory.spring.data.jpa.filtering.h2.reduction;

import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NumberEntityRepository extends JpaRepository<NumberEntity, Long>, WhitelistFilteringRepository<NumberEntity>, ReductionNumberEntityRepository {

}
