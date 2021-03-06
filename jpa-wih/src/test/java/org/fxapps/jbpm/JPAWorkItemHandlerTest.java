package org.fxapps.jbpm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.drools.core.process.instance.impl.WorkItemImpl;
import org.h2.tools.DeleteDbFiles;
import org.h2.tools.Server;
import org.junit.BeforeClass;
import org.junit.Test;
import org.kie.api.runtime.process.WorkItem;
import org.kie.api.runtime.process.WorkItemHandler;
import org.kie.api.runtime.process.WorkItemManager;

import bitronix.tm.TransactionManagerServices;
import bitronix.tm.resource.jdbc.PoolingDataSource;

public class JPAWorkItemHandlerTest {
	
	private static final String P_UNIT = "org.jbpm.test.jpaWIH";

	static WorkItemHandler handler;

	private static TestH2Server h2Server;

	@BeforeClass
	public static void setDS() throws InterruptedException {
		setupPoolingDataSource();
		ClassLoader classLoader = JPAWorkItemHandler.class.getClassLoader();
		handler = new JPAWorkItemHandler(P_UNIT, classLoader);
	}

	@Test
	public void createActionTest() throws Exception {
		Product product = create(new Product("some product", 0.1f));
		assertNotEquals(0, product.getId());
		removeProduct(product);
	}

	@Test
	public void getActionTest() throws Exception {
		Product newProd = create(new Product("some product", 0.1f));
		String id = String.valueOf(newProd.getId());
		Product product = getProduct(id);
		assertNotNull(product);
		removeProduct(product);
	}

	@Test
	public void queryActionTest() throws Exception {
		Product p1 = create(new Product("some prod", 2f));
		Product p2 =  create(new Product("other prod", 3f));
		List<Product> products = getAllProducts();
		assertEquals(2, products.size());
		removeProduct(p1);
		removeProduct(p2);
	}


	@Test
	public void modifyActionTest() throws Exception {
		String newDescription = "New prod description";
		Product product = create(new Product("prod desc", 1f));
		String id = String.valueOf(product.getId());
		assertNotNull(product);
		product.setDescription(newDescription);
		
		WorkItemImpl workItem = new WorkItemImpl();
		workItem.setParameter(JPAWorkItemHandler.P_ACTION,
				JPAWorkItemHandler.UPDATE_ACTION);
		workItem.setParameter(JPAWorkItemHandler.P_ENTITY, product);
		TransactionManagerServices.getTransactionManager().begin();
		handler.executeWorkItem(workItem, new TestWorkItemManager(workItem));
		TransactionManagerServices.getTransactionManager().commit();
		product = getProduct(id);
		assertEquals(newDescription, product.getDescription());
	}
	
	@Test
	public void removeActionTest() throws Exception {
		Product product = create(new Product("some des", 3f));
		String id = String.valueOf(product.getId());
		assertNotNull(getProduct(id));
		removeProduct(product);
		product = getProduct(id);
		assertNull(product);
	}
	
	@Test
	public void queryWithParameterActionTest() throws Exception {
		String DESC = "Cheese";
		Product p1 = new Product("Bread", 2f);
		Product p2 = new Product("Milk", 3f);
		Product p3 = new Product(DESC, 5f);
		create(p1);
		create(p2);
		create(p3);
		
		WorkItemImpl workItem = new WorkItemImpl();
		workItem.setParameter(JPAWorkItemHandler.P_ACTION,
				JPAWorkItemHandler.QUERY_ACTION);
		Map<String, Object> params = new HashMap<>();
		params.put("desc", DESC);
		workItem.setParameter(JPAWorkItemHandler.p_QUERY,
				"SELECT p FROM Product p where p.description = :desc");
		workItem.setParameter(JPAWorkItemHandler.P_QUERY_PARAMS, params);
		TransactionManagerServices.getTransactionManager().begin();
		handler.executeWorkItem(workItem, new TestWorkItemManager(workItem));
		TransactionManagerServices.getTransactionManager().commit();
		@SuppressWarnings("unchecked")
		List<Product> products = (List<Product>) workItem
				.getResult(JPAWorkItemHandler.P_QUERY_RESULTS);
		assertEquals(1, products.size());
		products = getAllProducts();
		assertEquals(3, products.size());
		for (Product product : products) {
			removeProduct(product);
		}
		products = getAllProducts();
		assertEquals(0, products.size());
	}

	private List<Product> getAllProducts() throws Exception {
		WorkItemImpl workItem = new WorkItemImpl();
		workItem.setParameter(JPAWorkItemHandler.P_ACTION,
				JPAWorkItemHandler.QUERY_ACTION);
		workItem.setParameter(JPAWorkItemHandler.p_QUERY,
				"SELECT p FROM Product p");
		TransactionManagerServices.getTransactionManager().begin();
		handler.executeWorkItem(workItem, new TestWorkItemManager(workItem));
		TransactionManagerServices.getTransactionManager().commit();
		@SuppressWarnings("unchecked")
		List<Product> products = (List<Product>) workItem.getResult(JPAWorkItemHandler.P_QUERY_RESULTS);
		return products;
	}
	
	private Product getProduct(String id) throws Exception {
		WorkItemImpl workItem = new WorkItemImpl();
		workItem.setParameter(JPAWorkItemHandler.P_ACTION,
				JPAWorkItemHandler.GET_ACTION);
		workItem.setParameter(JPAWorkItemHandler.P_TYPE,
				"org.fxapps.jbpm.Product");
		workItem.setParameter(JPAWorkItemHandler.P_ID, id);
		TransactionManagerServices.getTransactionManager().begin();
		handler.executeWorkItem(workItem, new TestWorkItemManager(workItem));
		TransactionManagerServices.getTransactionManager().commit();
		Product product = (Product) workItem
				.getResult(JPAWorkItemHandler.P_RESULT);
		return product;
	}
	
	private Product create(Product product)
			throws Exception {
		WorkItemImpl workItem = new WorkItemImpl();
		workItem.setParameter(JPAWorkItemHandler.P_ACTION,
				JPAWorkItemHandler.CREATE_ACTION);
		workItem.setParameter(JPAWorkItemHandler.P_ENTITY, product);
		TransactionManagerServices.getTransactionManager().begin();
		handler.executeWorkItem(workItem, new TestWorkItemManager(workItem));
		TransactionManagerServices.getTransactionManager().commit();
		Product result = (Product) workItem
				.getResult(JPAWorkItemHandler.P_RESULT);
		return result;
	}
	
	private void removeProduct(Product product) throws Exception {
		WorkItemImpl workItem = new WorkItemImpl();
		workItem.setParameter(JPAWorkItemHandler.P_ACTION,
				JPAWorkItemHandler.DELETE_ACTION);
		workItem.setParameter(JPAWorkItemHandler.P_ENTITY, product);
		TransactionManagerServices.getTransactionManager().begin();
		handler.executeWorkItem(workItem, new TestWorkItemManager(workItem));
		TransactionManagerServices.getTransactionManager().commit();
	}

	private class TestWorkItemManager implements WorkItemManager {

		private WorkItem workItem;

		TestWorkItemManager(WorkItem workItem) {
			this.workItem = workItem;
		}

		public void completeWorkItem(long id, Map<String, Object> results) {
			((WorkItemImpl) workItem).setResults(results);

		}

		public void abortWorkItem(long id) {

		}

		public void registerWorkItemHandler(String workItemName,
				WorkItemHandler handler) {
		}

	}

	public static PoolingDataSource setupPoolingDataSource() {
		h2Server = new TestH2Server();
		h2Server.start();
		PoolingDataSource pds = new PoolingDataSource();
		pds.setMaxPoolSize(10);
		pds.setMinPoolSize(10);
		pds.setUniqueName("jpaWIH");
		pds.setClassName("bitronix.tm.resource.jdbc.lrc.LrcXADataSource");
		pds.setAllowLocalTransactions(true);
		pds.getDriverProperties().put("user", "sa");
		pds.getDriverProperties().put("url", "jdbc:h2:mem:jpa-wih;MVCC=true");
		pds.getDriverProperties().put("driverClassName", "org.h2.Driver");
		pds.init();
		return pds;
	}

	private static class TestH2Server {
		private Server realH2Server;

		public void start() {
			if (realH2Server == null || !realH2Server.isRunning(false)) {
				try {
					realH2Server = Server.createTcpServer(new String[0]);
					realH2Server.start();
					System.out.println("Started H2 Server...");
				} catch (SQLException e) {
					throw new RuntimeException("can't start h2 server db", e);
				}
			}
		}

		@Override
		protected void finalize() throws Throwable {
			if (realH2Server != null) {
				System.out.println("Stopping H2 Server...");
				realH2Server.stop();
			}
			DeleteDbFiles.execute("", "target/jpa-wih", true);
			super.finalize();
		}

	}

}
