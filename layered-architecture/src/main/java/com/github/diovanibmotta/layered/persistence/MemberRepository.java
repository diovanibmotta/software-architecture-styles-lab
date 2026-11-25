package com.github.diovanibmotta.layered.persistence;

import com.github.diovanibmotta.layered.domain.Member;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MemberRepository {

    private final JdbcClient jdbc;

    MemberRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Member> findById(String id) {
        return jdbc.sql("SELECT id, name FROM members WHERE id = ?")
                .param(id).query(Member.class).optional();
    }

    /** Row lock held until the surrounding transaction ends: serializes concurrent loans of the same member. */
    public Optional<Member> findByIdForUpdate(String id) {
        return jdbc.sql("SELECT id, name FROM members WHERE id = ? FOR UPDATE")
                .param(id).query(Member.class).optional();
    }
}
