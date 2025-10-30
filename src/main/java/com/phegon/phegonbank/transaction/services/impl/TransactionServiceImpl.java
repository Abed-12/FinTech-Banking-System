package com.phegon.phegonbank.transaction.services.impl;

import com.phegon.phegonbank.account.entity.Account;
import com.phegon.phegonbank.account.repo.AccountRepo;
import com.phegon.phegonbank.auth_users.entity.User;
import com.phegon.phegonbank.auth_users.services.UserService;
import com.phegon.phegonbank.enums.TransactionStatus;
import com.phegon.phegonbank.enums.TransactionType;
import com.phegon.phegonbank.exceptions.BadRequestException;
import com.phegon.phegonbank.exceptions.InsufficientBalanceException;
import com.phegon.phegonbank.exceptions.InvalidTransactionException;
import com.phegon.phegonbank.exceptions.NotFoundException;
import com.phegon.phegonbank.notification.dtos.NotificationDTO;
import com.phegon.phegonbank.notification.services.NotificationService;
import com.phegon.phegonbank.res.Response;
import com.phegon.phegonbank.transaction.dtos.TransactionDTO;
import com.phegon.phegonbank.transaction.dtos.TransactionRequest;
import com.phegon.phegonbank.transaction.entity.Transaction;
import com.phegon.phegonbank.transaction.repo.TransactionRepo;
import com.phegon.phegonbank.transaction.services.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepo transactionRepo;
    private final AccountRepo accountRepo;
    private final NotificationService notificationService;
    private final UserService userService;
    private final ModelMapper modelMapper;

    @Override
    @Transactional
    public Response<?> createTransaction(TransactionRequest transactionRequest) {
        Transaction transaction = new Transaction();
        transaction.setTransactionType(transactionRequest.getTransactionType());
        transaction.setAmount(transactionRequest.getAmount());
        transaction.setDescription(transactionRequest.getDescription());

        switch (transactionRequest.getTransactionType()) {
            case DEPOSIT -> handleDeposit(transactionRequest, transaction);
            case WITHDRAWAL -> handleWithdrawal(transactionRequest, transaction);
            case TRANSFER -> handleTransfer(transactionRequest, transaction);
            default -> throw new InvalidTransactionException("Invalid transaction type");
        }

        transaction.setStatus(TransactionStatus.SUCCESS);

        Transaction savedTransaction = transactionRepo.save(transaction);

        // Send notification out
        sendTransactionNotifications(savedTransaction);

        return Response.builder()
                .statusCode(HttpStatus.OK.value())
                .message("Transaction created successfully")
                .build();
    }

    @Override
    public Response<List<TransactionDTO>> getTransactionsForMyAccount(String accountNumber, int page, int size) {
        // Get the currently logged-in user
        User user = userService.getCurrentLoggedInUser();

        // Find the account bt its number
        Account account = accountRepo.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new NotFoundException("Account not found"));

        // Make sure he accounts belongs to the user. an extra security check
        if (!account.getUser().getId().equals(user.getId())) {
            throw new BadRequestException("Account does not belong to the authenticated user");
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("transactionDate").descending());
        Page<Transaction> transactions = transactionRepo.findByAccount_AccountNumber(accountNumber, pageable);

        List<TransactionDTO> transactionDTOS = transactions.getContent().stream()
                .map(transaction -> modelMapper.map(transaction, TransactionDTO.class))
                .toList();

        return Response.<List<TransactionDTO>>builder()
                .statusCode(HttpStatus.OK.value())
                .message("Transactions retrieved")
                .data(transactionDTOS)
                .meta(Map.of(
                        "currentPage", transactions.getNumber(),
                        "totalItems", transactions.getTotalElements(),
                        "totalPages", transactions.getTotalPages(),
                        "pageSize", transactions. getSize()
                ))
                .build();
    }

    private void handleDeposit(TransactionRequest transactionRequest, Transaction transaction) {
        Account account = accountRepo.findByAccountNumber(transactionRequest.getAccountNumber())
                .orElseThrow(() -> new NotFoundException("Account not found"));

        account.setBalance(account.getBalance().add(transactionRequest.getAmount()));
        transaction.setAccount(account);

        accountRepo.save(account);
    }

    private void handleWithdrawal(TransactionRequest transactionRequest, Transaction transaction) {
        Account account = accountRepo.findByAccountNumber(transactionRequest.getAccountNumber())
                .orElseThrow(() -> new NotFoundException("Account not found"));

        if (account.getBalance().compareTo(transactionRequest.getAmount()) < 0) {
           throw new InsufficientBalanceException("Insufficient balance");
        }

        account.setBalance(account.getBalance().subtract(transactionRequest.getAmount()));
        transaction.setAccount(account);

        accountRepo.save(account);
    }

    private void handleTransfer(TransactionRequest transactionRequest, Transaction transaction) {
        Account sourceAccount = accountRepo.findByAccountNumber(transactionRequest.getAccountNumber())
                .orElseThrow(() -> new NotFoundException("Source account not found"));

        Account destinationAccount = accountRepo.findByAccountNumber(transactionRequest.getDestinationAccountNumber())
                .orElseThrow(() -> new NotFoundException("Destination account not found"));

        if (sourceAccount.getBalance().compareTo(transactionRequest.getAmount()) < 0) {
            throw new InsufficientBalanceException("Insufficient balance in source account");
        }

        sourceAccount.setBalance(sourceAccount.getBalance().subtract(transactionRequest.getAmount()));
        accountRepo.save(sourceAccount);

        destinationAccount.setBalance(destinationAccount.getBalance().add(transactionRequest.getAmount()));
        accountRepo.save(destinationAccount);

        transaction.setAccount(sourceAccount);
        transaction.setSourceAccount(sourceAccount.getAccountNumber());
        transaction.setDestinationAccount(destinationAccount.getAccountNumber());
    }

    private void sendTransactionNotifications(Transaction transaction) {
        User user = transaction.getAccount().getUser();
        String subject;
        String template;

        Map<String, Object> templateVariables = new HashMap<>();
        templateVariables.put("name", user.getFirstName());
        templateVariables.put("amount", transaction.getAmount());
        templateVariables.put("accountNumber", transaction.getAccount().getAccountNumber());
        templateVariables.put("date", transaction.getTransactionDate());
        templateVariables.put("balance", transaction.getAccount().getBalance());

        if (transaction.getTransactionType() == TransactionType.DEPOSIT) {
            subject = "Credit Alert";
            template = "credit-alert";

            NotificationDTO notificationEmailToSendOut = NotificationDTO.builder()
                    .recipient(user.getEmail())
                    .subject(subject)
                    .templateName(template)
                    .templateVariables(templateVariables)
                    .build();

            notificationService.sendEmail(notificationEmailToSendOut, user);
        } else if (transaction.getTransactionType() == TransactionType.WITHDRAWAL) {
            subject = "Debit Alert";
            template = "debit-alert";

            NotificationDTO notificationEmailToSendOut = NotificationDTO.builder()
                    .recipient(user.getEmail())
                    .subject(subject)
                    .templateName(template)
                    .templateVariables(templateVariables)
                    .build();

            notificationService.sendEmail(notificationEmailToSendOut, user);
        } else if (transaction.getTransactionType() == TransactionType.TRANSFER) {
            subject = "Debit Alert";
            template = "debit-alert";

            NotificationDTO notificationEmailToSendOut = NotificationDTO.builder()
                    .recipient(user.getEmail())
                    .subject(subject)
                    .templateName(template)
                    .templateVariables(templateVariables)
                    .build();

            notificationService.sendEmail(notificationEmailToSendOut, user);

            // Receiver CREDIT alert
            Account destinationAccount = accountRepo.findByAccountNumber(transaction.getDestinationAccount())
                    .orElseThrow(() -> new NotFoundException("Destination account not found"));

            User receiver = destinationAccount.getUser();

            Map<String, Object> receiverVariables = new HashMap<>();
            receiverVariables.put("name", user.getFirstName());
            receiverVariables.put("amount", transaction.getAmount());
            receiverVariables.put("accountNumber", destinationAccount.getAccountNumber());
            receiverVariables.put("date", transaction.getTransactionDate());
            receiverVariables.put("balance", destinationAccount.getBalance());

            NotificationDTO notificationEmailToSendOutToReceiver = NotificationDTO.builder()
                    .recipient(receiver.getEmail())
                    .subject("Credit Alert")
                    .templateName("credit-alert")
                    .templateVariables(receiverVariables)
                    .build();

            notificationService.sendEmail(notificationEmailToSendOutToReceiver, receiver);
        }
    }
}
