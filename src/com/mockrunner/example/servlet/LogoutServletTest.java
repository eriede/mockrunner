package com.mockrunner.example.servlet;

import com.mockrunner.servlet.ServletTestCaseAdapter;

/**
 * Example test for {@link LogoutServlet}.
 * Demonstrates the usage of {@link com.mockrunner.servlet.ServletTestModule} 
 * resp. {@link com.mockrunner.servlet.ServletTestCaseAdapter}
 * with and without a filter.
 */
public class LogoutServletTest extends ServletTestCaseAdapter
{
    protected void setUp() throws Exception
    {
        super.setUp();
        createServlet(LogoutServlet.class);
    }
    
    public void testDoLogout() throws Exception
    {
        addRequestParameter("logout", "true");
        doPost();
        assertFalse(getWebMockObjectFactory().getMockSession().isValid());
    }
    
    public void testDoLogoutWithFilteredImageButton() throws Exception
    {
        addRequestParameter("logout.x", "11");
        addRequestParameter("logout.y", "11");
        doPost();
        assertTrue(getWebMockObjectFactory().getMockSession().isValid());
        createFilter(ImageButtonFilter.class);
        setDoChain(true);
        doPost();
        assertFalse(getWebMockObjectFactory().getMockSession().isValid());
    }
}
