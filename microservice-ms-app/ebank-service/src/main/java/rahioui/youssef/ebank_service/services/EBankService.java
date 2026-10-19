package rahioui.youssef.ebank_service.services;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import rahioui.youssef.ebank_service.entities.BankAccount;
import rahioui.youssef.ebank_service.feign.CustomerRestClient;
import rahioui.youssef.ebank_service.repository.BankAccountRepository;

@Service
public class EBankService {
    private final BankAccountRepository accountRepository;
    private final CustomerRestClient customerRestClient;

    public EBankService(BankAccountRepository accountRepository, CustomerRestClient customerRestClient) {
        this.accountRepository = accountRepository;
        this.customerRestClient = customerRestClient;
    }

    public List<BankAccount> getAllBankAccounts() {
        List<BankAccount> accounts = accountRepository.findAll();
        accounts.forEach(a -> a.setCustomer(
                customerRestClient.getCustomerById(a.getCustomerId())));
        return accounts;
    }

    public BankAccount getBankAccountById(String id) {
        BankAccount bankAccount = accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Account not Found!"));
        bankAccount.setCustomer(customerRestClient.getCustomerById(bankAccount.getCustomerId()));
        return bankAccount;
    }

    public BankAccount save(BankAccount bankAccount) {
        try {
            customerRestClient.getCustomerById(bankAccount.getCustomerId());
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
        bankAccount.setId(UUID.randomUUID().toString());
        bankAccount.setCreatedAt(new Date());
        return accountRepository.save(bankAccount);
    }
}
