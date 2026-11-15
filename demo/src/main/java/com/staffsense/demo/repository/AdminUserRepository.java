package com.staffsense.demo.repository;

import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Чтения для экрана «Пользователи» (ADMIN).
 *
 * <p>Отдельный репозиторий, а не правка {@link UserRepository}: каталог
 * сотрудников и администрирование отвечают на разные вопросы. Каталог
 * показывает только активных сотрудников роли EMPLOYEE и сортирует по риску,
 * администратору же нужны все учётные записи — включая заблокированные и
 * служебные. Держать оба набора запросов в одном интерфейсе значило бы
 * смешать две видимости в одном месте.
 *
 * <p>Spring Data допускает несколько репозиториев на одну сущность; бин
 * называется {@code adminUserRepository} и с {@code userRepository} не
 * пересекается.
 */
@Repository
public interface AdminUserRepository extends JpaRepository<User, Long> {

    /**
     * Все пользователи без фильтра по роли: поиск по ФИО, email и отделу.
     *
     * <p>Пустой поиск — это {@code pattern = "%%"}, поэтому отдельный метод
     * «без фильтра» не нужен и NULL-параметров в JPQL нет.
     */
    @Query("""
            SELECT u FROM User u
            WHERE LOWER(u.fullName) LIKE :pattern
               OR LOWER(u.email) LIKE :pattern
               OR LOWER(u.department) LIKE :pattern
            """)
    Page<User> searchAll(@Param("pattern") String pattern, Pageable pageable);

    /** То же с фильтром по роли. */
    @Query("""
            SELECT u FROM User u
            WHERE u.role = :role
              AND (LOWER(u.fullName) LIKE :pattern
                   OR LOWER(u.email) LIKE :pattern
                   OR LOWER(u.department) LIKE :pattern)
            """)
    Page<User> searchByRole(@Param("role") Role role,
                            @Param("pattern") String pattern,
                            Pageable pageable);

    /**
     * Проверка уникальности email без учёта регистра.
     *
     * <p>Вход в систему ищет пользователя точным совпадением, но заводить
     * «Ivan@x» рядом с «ivan@x» нельзя: одна из двух учётных записей
     * окажется недоступной для входа.
     */
    boolean existsByEmailIgnoreCase(String email);

    /** Сколько активных учётных записей с ролью — для правила «последний админ». */
    long countByRoleAndActive(Role role, boolean active);
}
