package rahioui.youssef.ebank_service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import rahioui.youssef.ebank_service.entities.BankAccount;

public interface BankAccountRepository extends JpaRepository<BankAccount, String> {

    List<BankAccount> findByCustomerId(long id);

}
