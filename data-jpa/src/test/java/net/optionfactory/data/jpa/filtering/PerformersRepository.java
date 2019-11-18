package net.optionfactory.data.jpa.filtering;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PerformersRepository extends JpaRepository<Performer, Long>, WhitelistFilteringRepository<Performer> {

    Optional<Performer> findByIdAndName(long id, String name);

    Slice<Performer> findByName(String name, Pageable pr);

    default List<Performer> findAllByName(String name, FilterRequest fr) {
        return findAll((root, query, cb) -> cb.equal(root.get("name"), name), fr);
    }
}
