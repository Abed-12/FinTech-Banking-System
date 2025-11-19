package com.phegon.phegonbank.transaction.repo;

import com.phegon.phegonbank.transaction.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepo extends JpaRepository<Transaction, Long> {

    // For Paginated
    @Query("SELECT t FROM Transaction t WHERE t.account.accountNumber = :accountNumber " +
            "OR (t.transactionType = 'TRANSFER' AND t.destinationAccount = :accountNumber) " +
            "ORDER BY t.transactionDate DESC")
    Page<Transaction> findByAccount_AccountNumber(String accountNumber, Pageable pageable);

    @Query("SELECT t FROM Transaction t WHERE t.account.accountNumber = :accountNumber " +
            "OR (t.transactionType = 'TRANSFER' AND t.destinationAccount = :accountNumber) " +
            "ORDER BY t.transactionDate DESC")
    List<Transaction> findByAccount_AccountNumber(String accountNumber);

}
