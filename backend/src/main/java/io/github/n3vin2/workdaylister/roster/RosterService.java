package io.github.n3vin2.workdaylister.roster;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The Roster: the set of Companies the user is tracking, replaced wholesale by each CSV upload. */
@Service
public class RosterService {

    private final CompanyRepository companies;

    RosterService(CompanyRepository companies) {
        this.companies = companies;
    }

    /**
     * Replaces the Roster with the given entries, in one transaction. Entries with the same Career
     * Site coordinates collapse to one Company (the first occurrence's name wins). A Company
     * already in the Roster with matching coordinates keeps its id and adopts the entry's name and
     * URL; a Company absent from the entries is deleted, together with anything that hangs off it.
     *
     * @return the new Roster, sorted by name
     */
    @Transactional
    public List<Company> replace(List<RosterEntry> entries) {
        Map<CareerSite, RosterEntry> wanted = new LinkedHashMap<>();
        for (RosterEntry entry : entries) {
            wanted.putIfAbsent(entry.careerSite(), entry);
        }

        List<Company> removed = new ArrayList<>();
        for (Company existing : companies.findAll()) {
            RosterEntry match = wanted.remove(existing.getCareerSite());
            if (match == null) {
                removed.add(existing);
            } else {
                existing.adopt(match);
            }
        }
        companies.deleteAll(removed);
        companies.saveAll(wanted.values().stream().map(Company::new).toList());
        return companies.findAllByOrderByNameAsc();
    }

    /** The current Roster, sorted by name. */
    @Transactional(readOnly = true)
    public List<Company> companies() {
        return companies.findAllByOrderByNameAsc();
    }

    /** One Company of the Roster, or empty when no Company has that id. */
    @Transactional(readOnly = true)
    public Optional<Company> find(long id) {
        return companies.findById(id);
    }
}
