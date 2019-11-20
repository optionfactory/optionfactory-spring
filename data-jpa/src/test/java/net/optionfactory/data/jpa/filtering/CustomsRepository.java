package net.optionfactory.data.jpa.filtering;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomsRepository extends JpaRepository<CustomEntity, Long>, WhitelistFilteringRepository<CustomEntity> {
}
