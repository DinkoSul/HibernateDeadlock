package hr.java.hibernate.deadlock.demo;

import hr.java.hibernate.deadlock.demo.model.Publisher;
import jakarta.persistence.OptimisticLockException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class DeadLockExample {

    private static final Logger logger = LoggerFactory.getLogger(DeadLockExample.class);

    private static Long createPublisher(Session session, String name) {
        Publisher publisher = new Publisher();
        publisher.setName(name);
        session.save(publisher);
        return publisher.getId();
    }

    private static void updatePublishers(SessionFactory sessionFactory, String prefix, Long... ids) {
        try (Session session = sessionFactory.openSession()) {
            Transaction tx = session.beginTransaction();
            for (Long id : ids) {
                Thread.sleep(300);
                Publisher publisher = session.byId(Publisher.class).load(id);
                publisher.setName(prefix + " " + publisher.getName());
            }
            tx.commit();
        } catch (OptimisticLockException e) {
            logger.error("lock exception with prefix " + prefix);
        } catch (
                InterruptedException ignored) {
        }
    }

    public static void main(String[] args) throws InterruptedException {
        SessionFactory sessionFactory = new Configuration().configure().buildSessionFactory();
        Long publisherAId;
        Long publisherBId;
        try (Session session = sessionFactory.openSession()) {
            Transaction tx = session.beginTransaction();
            session.createQuery("delete from Publisher").executeUpdate();
            publisherAId = createPublisher(session, "A");
            publisherBId = createPublisher(session, "B");
            tx.commit();
        }
        ExecutorService executor = Executors.newFixedThreadPool(2);
        executor.submit(() -> updatePublishers(sessionFactory, "session1", publisherAId, publisherBId));
        executor.submit(() -> updatePublishers(sessionFactory, "session2", publisherBId, publisherAId));
        executor.shutdown();
        if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
            executor.shutdownNow();
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                System.out.println("Executor did not terminate");
            }
        }
        try (Session session = sessionFactory.openSession()) {
            Query<Publisher> query = session.createQuery("from Publisher p order by p.name", Publisher.class);
            String result = query.list().stream().map(Publisher::getName).collect(Collectors.joining(", "));
            System.out.println(result);
        }
    }
}