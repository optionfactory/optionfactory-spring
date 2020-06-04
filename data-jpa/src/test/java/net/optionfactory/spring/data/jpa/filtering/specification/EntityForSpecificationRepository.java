package net.optionfactory.spring.data.jpa.filtering.specification;

import java.util.List;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntityForSpecificationRepository extends JpaRepository<EntityForSpecification, Long>, WhitelistFilteringRepository<EntityForSpecification> {


    default List<EntityForSpecification> findAllByName(String name, FilterRequest fr) {
         return findAll((root, query, cb) -> cb.equal(root.get("name"), name), fr);
     }
}
