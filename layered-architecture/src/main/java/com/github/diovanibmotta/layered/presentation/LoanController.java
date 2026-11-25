package com.github.diovanibmotta.layered.presentation;

import com.github.diovanibmotta.layered.business.LoanService;
import com.github.diovanibmotta.layered.domain.Loan;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
class LoanController {

    record BorrowRequest(@NotBlank String isbn, @NotBlank String memberId) {
    }

    private final LoanService loans;

    LoanController(LoanService loans) {
        this.loans = loans;
    }

    @PostMapping("/loans")
    @ResponseStatus(HttpStatus.CREATED)
    Loan borrow(@Valid @RequestBody BorrowRequest request) {
        return loans.borrow(request.isbn(), request.memberId());
    }

    @PostMapping("/loans/{loanId}/return")
    Loan giveBack(@PathVariable long loanId) {
        return loans.giveBack(loanId);
    }

    @GetMapping("/members/{memberId}/loans")
    List<Loan> history(@PathVariable String memberId) {
        return loans.loansOf(memberId);
    }
}
