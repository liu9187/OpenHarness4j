package io.openharness4j.personal;

import java.util.List;
import java.util.Optional;

public interface PersonalWorkspaceStore {

    PersonalWorkspace getOrCreate(String userId);

    Optional<PersonalWorkspace> get(String userId);

    void save(PersonalWorkspace workspace);

    List<PersonalWorkspace> list();
}
