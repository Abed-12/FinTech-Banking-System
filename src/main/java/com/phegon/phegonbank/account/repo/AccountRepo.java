package com.phegon.phegonbank.account.repo;

import com.phegon.phegonbank.account.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepo extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountNumber(String accountNumber);
    List<Account> findByUserId(Long userId);

}
