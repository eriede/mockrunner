package com.mockrunner.example.ejb;

import javax.naming.InitialContext;
import javax.rmi.PortableRemoteObject;

import org.mockejb.MockContainer;
import org.mockejb.MockContext;
import org.mockejb.MockEjbObject;
import org.mockejb.SessionBeanDescriptor;

import com.mockrunner.example.ejb.interfaces.PaySession;
import com.mockrunner.example.ejb.interfaces.PaySessionHome;
import com.mockrunner.jdbc.JDBCTestCaseAdapter;
import com.mockrunner.jdbc.StatementResultSetHandler;
import com.mockrunner.mock.jdbc.MockResultSet;
import com.mockrunner.mock.jdbc.MockUserTransaction;

/**
 * Example test for {@link PaySessionBean}. This example demonstrates
 * howto use {@link com.mockrunner.jdbc.JDBCTestModule} and the MockEJB
 * framework in conjunction. The tests are similar to 
 * {@link com.mockrunner.example.jdbc.PayActionTest} but instead of
 * an action we test an EJB. This example works with the simulated JDBC
 * environment of Mockrunner. Note that you have to deploy a
 * <code>UserTransaction</code> into the mock container.
 * This has to be done in a static initializer, because MockEJB stores
 * the <code>UserTransaction</code> in a static field and always
 * uses this instance. So the Mockrunner practice to create
 * the mock objects in the <code>setUp</code> method cannot
 * be used in this case. In order to reset the state before
 * executing a test method, {@link com.mockrunner.mock.jdbc.MockUserTransaction#reset}
 * has to be called in the <code>setUp</code> method.
 * Since the <code>UserTransaction</code> is only a mock implementation, 
 * it will not work with a real database.
 */
public class PaySessionTest extends JDBCTestCaseAdapter
{
    private static MockUserTransaction transaction;
    private MockEjbObject ejbObject;
    private PaySession bean;
    private StatementResultSetHandler statementHandler;
    
    static
    {
        transaction = new MockUserTransaction();
        MockContext.add("javax.transaction.UserTransaction", transaction);
    }
    
    protected void setUp() throws Exception
    {
        super.setUp();
        transaction.reset();
        SessionBeanDescriptor beanDescriptor = new SessionBeanDescriptor("com/mockrunner/example/PaySession", PaySessionHome.class, PaySession.class, PaySessionBean.class);
        ejbObject = MockContainer.deploy(beanDescriptor);
        MockContext.add("java:comp/env/jdbc/MySQLDB", getJDBCMockObjectFactory().getMockDataSource());
        InitialContext context = new InitialContext();
        Object home = context.lookup("com/mockrunner/example/PaySession");
        PaySessionHome payHome = (PaySessionHome)PortableRemoteObject.narrow(home, PaySessionHome.class );
        bean = (PaySession)payHome.create();
        statementHandler = getJDBCMockObjectFactory().getMockConnection().getStatementResultSetHandler();
    }
    
    private void createValidCustomerResult()
    {
        MockResultSet result = statementHandler.createResultSet();
        result.addColumn("name", new String[] {"MyName"});
        statementHandler.prepareResultSet("select name", result);
    }

    private void createValidBillResult()
    {
        MockResultSet result = statementHandler.createResultSet();
        result.addColumn("id", new String[] {"1"});
        result.addColumn("customerid", new String[] {"1"});
        result.addColumn("amount", new Double[] {new Double(100)});
        statementHandler.prepareResultSet("select * from openbills", result);
    }

    public void testUnknownCustomer() throws Exception
    {
        MockResultSet result = statementHandler.createResultSet();
        result.addColumn("name");
        statementHandler.prepareResultSet("select name", result);
        try
        {
            bean.payBill("1", "1", 100);
            fail();
        }
        catch(PaySessionException exc)
        {
            assertEquals(PaySessionException.UNKNOWN_CUSTOMER, exc.getCode());
        }
        assertTrue(ejbObject.getEjbContext().getRollbackOnly());
        verifySQLStatementExecuted("select name");
        verifySQLStatementNotExecuted("delete from openbills");
        verifySQLStatementNotExecuted("insert into paidbills");
        verifyAllResultSetsClosed();
        verifyAllStatementsClosed();
        verifyConnectionClosed();
    }
    
    public void testUnknownBill() throws Exception
    {
        createValidCustomerResult();
        MockResultSet result = statementHandler.createResultSet();
        result.addColumn("id");
        result.addColumn("customerid");
        result.addColumn("amount");
        statementHandler.prepareResultSet("select * from openbills", result);
        try
        {
            bean.payBill("1", "1", 100);
            fail();
        }
        catch(PaySessionException exc)
        {
            assertEquals(PaySessionException.UNKNOWN_BILL, exc.getCode());
        }
        assertTrue(ejbObject.getEjbContext().getRollbackOnly());
        verifySQLStatementExecuted("select * from openbills");
        verifySQLStatementNotExecuted("delete from openbills");
        verifySQLStatementNotExecuted("insert into paidbills");
        verifyAllResultSetsClosed();
        verifyAllStatementsClosed();
        verifyConnectionClosed();
    }
    
    public void testCustomerIdMismatch() throws Exception
    {
        createValidCustomerResult();
        createValidBillResult();
        try
        {
            bean.payBill("2", "1", 100);
            fail();
        }
        catch(PaySessionException exc)
        {
            assertEquals(PaySessionException.WRONG_BILL_FOR_CUSTOMER, exc.getCode());
        }
        assertTrue(ejbObject.getEjbContext().getRollbackOnly());
        verifySQLStatementExecuted("select * from openbills");
        verifySQLStatementNotExecuted("delete from openbills");
        verifySQLStatementNotExecuted("insert into paidbills");
        verifyAllResultSetsClosed();
        verifyAllStatementsClosed();
        verifyConnectionClosed();
    }
    
    public void testAmountMismatch() throws Exception
    {
        createValidCustomerResult();
        createValidBillResult();
        try
        {
            bean.payBill("1", "1", 200);
            fail();
        }
        catch(PaySessionException exc)
        {
            assertEquals(PaySessionException.WRONG_AMOUNT_FOR_BILL, exc.getCode());
        }
        assertTrue(ejbObject.getEjbContext().getRollbackOnly());
        verifySQLStatementExecuted("select * from openbills");
        verifySQLStatementNotExecuted("delete from openbills");
        verifySQLStatementNotExecuted("insert into paidbills");
        verifyAllResultSetsClosed();
        verifyAllStatementsClosed();
        verifyConnectionClosed();
    }

    public void testValidTransaction() throws Exception
    {
        createValidCustomerResult();
        createValidBillResult();
        bean.payBill("1", "1", 100);
        assertFalse(ejbObject.getEjbContext().getRollbackOnly()); 
        verifySQLStatementExecuted("delete from openbills where id=1");
        verifySQLStatementExecuted("insert into paidbills values(1,1,100.0)");
        verifyAllResultSetsClosed();
        verifyAllStatementsClosed();
        verifyConnectionClosed();
    }
}
