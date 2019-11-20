package net.optionfactory.data.jpa.filtering.chaining;

 import java.util.List;
 import java.util.Optional;
import net.optionfactory.data.jpa.filtering.FilterRequest;
import net.optionfactory.data.jpa.filtering.WhitelistFilteringRepository;
 import org.springframework.data.domain.Pageable;
 import org.springframework.data.domain.Slice;
 import org.springframework.data.jpa.repository.JpaRepository;
 
public interface ActivitiesRepository extends JpaRepository<Activity, Long>, WhitelistFilteringRepository<Activity> {
 
    Optional<Activity> findByIdAndName(long id, String name);
 
    Slice<Activity> findByName(String name, Pageable pr);
 
    default List<Activity> findAllByName(String name, FilterRequest fr) {
         return findAll((root, query, cb) -> cb.equal(root.get("name"), name), fr);
     }
 }
