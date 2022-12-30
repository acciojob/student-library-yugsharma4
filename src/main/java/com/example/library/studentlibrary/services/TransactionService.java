package com.example.library.studentlibrary.services;

import com.example.library.studentlibrary.models.*;
import com.example.library.studentlibrary.repositories.BookRepository;
import com.example.library.studentlibrary.repositories.CardRepository;
import com.example.library.studentlibrary.repositories.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class TransactionService {

    @Autowired
    BookRepository bookRepository5;

    @Autowired
    CardRepository cardRepository5;

    @Autowired
    TransactionRepository transactionRepository5;

    @Value("${books.max_allowed}")
    int max_allowed_books;

    @Value("${books.max_allowed_days}")
    int getMax_allowed_days;

    @Value("${books.fine.per_day}")
    int fine_per_day;

    public String issueBook(int cardId, int bookId) throws Exception {
        Book book = bookRepository5.findById(bookId).get();
        Card card = cardRepository5.findById(cardId).get();
        List<Book> books = card.getBooks();
        String transactionId = null;

        //check whether bookId and cardId already exist
        if(!bookRepository5.existsById(bookId)  || book.isAvailable() == false){
            Transaction transaction = Transaction.builder()
                    .transactionStatus(TransactionStatus.FAILED)
                    .isIssueOperation(true)
                    .transactionId(UUID.randomUUID().toString())
                    .fineAmount(0)
                    .build();
            transactionRepository5.save(transaction);
            throw new Exception("Book is either unavailable or not present");
        }else if(!cardRepository5.existsById(cardId) || card.getCardStatus().equals(CardStatus.DEACTIVATED)){
            Transaction transaction = Transaction.builder()
                    .transactionStatus(TransactionStatus.FAILED)
                    .isIssueOperation(true)
                    .transactionId(UUID.randomUUID().toString())
                    .fineAmount(0)
                    .build();
            transactionRepository5.save(transaction);
            throw new Exception("Card is invalid");
        }else if(books.size() > max_allowed_books){
            Transaction transaction = Transaction.builder()
                    .transactionStatus(TransactionStatus.FAILED)
                    .isIssueOperation(true)
                    .transactionId(UUID.randomUUID().toString())
                    .fineAmount(0)
                    .build();
            transactionRepository5.save(transaction);
            throw new Exception("Book limit has reached for this card");
        }else{
            Transaction transaction = Transaction.builder()
                    .book(book)
                    .card(card)
                    .transactionStatus(TransactionStatus.SUCCESSFUL)
                    .isIssueOperation(true)
                    .transactionId(UUID.randomUUID().toString())
                    .fineAmount(0)
                    .transactionDate(new Date())
                    .build();
            //first book
            if(books == null) {
                books = new ArrayList<>();
            }
            books.add(book);
            card.setBooks(books);

            List<Transaction> oldTransactions = book.getTransactions();
            //first transaction
            if(oldTransactions == null){
                oldTransactions = new ArrayList<>();
            }
            oldTransactions.add(transaction);
            book.setCard(card);
            book.setAvailable(false);
            bookRepository5.updateBook(book);

            transaction = transactionRepository5.save(transaction);
            transactionId = transaction.getTransactionId();
        }

        return transactionId;

        //conditions required for successful transaction of issue book:
        //1. book is present and available
        // If it fails: throw new Exception("Book is either unavailable or not present");
        //2. card is present and activated
        // If it fails: throw new Exception("Card is invalid");
        //3. number of books issued against the card is strictly less than max_allowed_books
        // If it fails: throw new Exception("Book limit has reached for this card");
        //If the transaction is successful, save the transaction to the list of transactions and return the id

        //Note that the error message should match exactly in all cases
        //return transactionId instead
    }

    public Transaction returnBook(int cardId, int bookId) throws Exception{

        List<Transaction> transactions = transactionRepository5.find(cardId, bookId,TransactionStatus.SUCCESSFUL, true);
        Transaction transaction = transactions.get(transactions.size() - 1);

        //for the given transaction calculate the fine amount considering the book has been returned exactly when this function is called
        Book book = transaction.getBook();
        Card card = transaction.getCard();
        Date issueDate = transaction.getTransactionDate();
        Date currentDate = new Date();
        int beforeFine = transaction.getFineAmount();


        //Calculate difference between two days

        long dateBeforeInMs = issueDate.getTime();
        long dateAfterInMs = currentDate.getTime();

        long timeDiff = Math.abs(dateAfterInMs - dateBeforeInMs);

        long daysDiff = TimeUnit.DAYS.convert(timeDiff, TimeUnit.MILLISECONDS);
        int delay = getMax_allowed_days - (int)daysDiff;


        //if book returned after 15 days --> no of delayDay * fine_per_day
        if(delay < 0){
            beforeFine += delay * fine_per_day;
        }

        //make the book available for other users
        book.setAvailable(true);

        //make a new transaction for return book which contains the fine amount as well

        Transaction returnBookTransaction  = null;
        returnBookTransaction = Transaction.builder()
                .transactionDate(new Date())
                .transactionStatus(TransactionStatus.SUCCESSFUL)
                .fineAmount(beforeFine)
                .book(book)
                .card(card)
                .isIssueOperation(false)
                .build();

        List<Book> previousBooks = card.getBooks();
        previousBooks.remove(book);
        card.setBooks(previousBooks);
        card.setUpdatedOn(new Date());

        book.setCard(null);
        List<Transaction> previousTransactions = book.getTransactions();
        previousTransactions.add(transaction);
        book.setTransactions(previousTransactions);
        bookRepository5.updateBook(book);


        transactionRepository5.save(returnBookTransaction);
        return returnBookTransaction; //return the transaction after updating all details
    }
}