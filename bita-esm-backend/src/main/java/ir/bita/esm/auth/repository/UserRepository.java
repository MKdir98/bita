package ir.bita.esm.auth.repository;

import ir.bita.esm.auth.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for User entity.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Finds a user by mobile number.
     */
    Optional<User> findByMobileNumber(String mobileNumber);

    /**
     * Finds an active user by mobile number.
     */
    Optional<User> findByMobileNumberAndActiveTrue(String mobileNumber);

    /**
     * Checks if a user exists with the given mobile number.
     */
    boolean existsByMobileNumber(String mobileNumber);

    /**
     * Checks if an active user exists with the given mobile number.
     */
    boolean existsByMobileNumberAndActiveTrue(String mobileNumber);

    /**
     * Finds all non-deleted users with pagination.
     */
    Page<User> findByDeletedFalse(Pageable pageable);

    /**
     * Searches users by mobile number or full name.
     */
    @Query("SELECT u FROM User u WHERE u.deleted = false AND " +
           "(LOWER(u.mobileNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<User> searchUsers(@Param("search") String search, Pageable pageable);

    /**
     * Finds a non-deleted user by ID.
     */
    Optional<User> findByIdAndDeletedFalse(Long id);
}
