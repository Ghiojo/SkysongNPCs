package org.skysongdev.skysongnpcs.storage;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skysongdev.skysongnpcs.api.groups.GroupMember;
import org.skysongdev.skysongnpcs.api.groups.PlayerGroup;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The snpc_groups SQL, run on H2 in MySQL mode. */
class SqlGroupStoreTest {
    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();

    private JdbcDataSource dataSource;
    private final List<String> log = new ArrayList<>();

    @BeforeEach
    void setUp() {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
    }

    private SqlGroupStore store() {
        return new SqlGroupStore(dataSource, log::add);
    }

    @Test
    void savesAndLoadsCharacterAndServerGroupsWithTheirMembers() {
        PlayerGroup friends = new PlayerGroup("friends", NpcOwner.character(ALICE, "Pirate Queen"),
                Set.of(GroupMember.everyCharacter(BOB), GroupMember.character(ALICE, "Second Mate")));
        PlayerGroup guards = new PlayerGroup("guards", NpcOwner.server(), Set.of());
        store().save(friends);
        store().save(guards);

        assertEquals(Set.of(friends, guards), new HashSet<>(store().loadAll()));
        assertTrue(log.isEmpty());
    }

    @Test
    void savingAgainReplacesTheOwnerAndMembers() {
        store().save(new PlayerGroup("friends", NpcOwner.character(ALICE, "Default"), Set.of(GroupMember.everyCharacter(BOB))));
        PlayerGroup changed = new PlayerGroup("friends", NpcOwner.server(), Set.of(GroupMember.character(ALICE, "Default")));

        store().save(changed);

        assertEquals(List.of(changed), store().loadAll());
    }

    @Test
    void deletingRemovesTheGroupAndItsMembers() {
        store().save(new PlayerGroup("friends", NpcOwner.server(), Set.of(GroupMember.everyCharacter(BOB))));

        store().delete("friends");
        store().save(new PlayerGroup("friends", NpcOwner.server(), Set.of()));

        assertEquals(List.of(new PlayerGroup("friends", NpcOwner.server(), Set.of())), store().loadAll());
    }
}
