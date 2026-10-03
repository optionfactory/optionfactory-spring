package net.optionfactory.spring.data.jpa.filtering;

import jakarta.persistence.EntityManager;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactoryBean;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/// The repository factory bean installed by [EnableJpaWhitelistFilteringRepositories]: a spring
/// data jpa factory bean whose repositories refuse to run outside a transaction.
///
/// [EnableJpaWhitelistFilteringRepositories] turns spring data's default transactions off, so a
/// repository never opens a transaction of its own and joins the caller's. Without one, a read
/// would silently run on a throwaway entity manager, outside the unit of work the caller believes
/// it is in, and a write would fail deep in the persistence layer. Each repository method, derived
/// queries and custom fragments included, therefore fails fast with an
/// `IllegalTransactionStateException` naming the repository and the method when no transaction is
/// active. `Object` methods (`toString`, `equals`, `hashCode`) are not checked.
///
/// The check is skipped when [EnableJpaWhitelistFilteringRepositories#enableDefaultTransactions()]
/// is turned on: the repositories then open their own transactions, as spring data's do.
///
/// @param <T> the repository type
/// @param <S> the entity type
/// @param <ID> the entity identifier type
public class WhitelistFilteringRepositoryFactoryBean<T extends Repository<S, ID>, S, ID> extends JpaRepositoryFactoryBean<T, S, ID> {

    private boolean enableDefaultTransactions = true;

    /// @param repositoryInterface the repository interface to create
    public WhitelistFilteringRepositoryFactoryBean(Class<? extends T> repositoryInterface) {
        super(repositoryInterface);
    }

    /// @param enableDefaultTransactions whether the repositories open their own transactions, in
    /// which case they are not required to run inside one
    @Override
    public void setEnableDefaultTransactions(boolean enableDefaultTransactions) {
        super.setEnableDefaultTransactions(enableDefaultTransactions);
        this.enableDefaultTransactions = enableDefaultTransactions;
    }

    /// @param entityManager the entity manager the repositories use
    /// @return spring data's factory, with the transaction check added to every repository it
    /// creates unless default transactions are enabled
    @Override
    protected RepositoryFactorySupport createRepositoryFactory(EntityManager entityManager) {
        final var factory = super.createRepositoryFactory(entityManager);
        if (!enableDefaultTransactions) {
            factory.addRepositoryProxyPostProcessor((proxy, information) -> proxy.addAdvice(new TransactionRequired(information.getRepositoryInterface().getSimpleName())));
        }
        return factory;
    }

    private record TransactionRequired(String repository) implements MethodInterceptor {

        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            if (invocation.getMethod().getDeclaringClass() != Object.class && !TransactionSynchronizationManager.isActualTransactionActive()) {
                throw new IllegalTransactionStateException(String.format("%s.%s requires a transaction: repositories enabled by @EnableJpaWhitelistFilteringRepositories never open their own, and must be called inside the caller's", repository, invocation.getMethod().getName()));
            }
            return invocation.proceed();
        }
    }
}
