import java.io.IOException;
import java.util.Arrays;

import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;

public class TestBrowser
{
	String url = "https://bdgastore.com";
	Browser brows = new Browser();
	@BeforeTest //annotation
	public void initialUrlTest() throws InterruptedException
	{
		System.out.println("Initial Test Commencing: Opening Browswer and Loading Webpage...");
		//Assert that the URL was set.
		brows.setUrl(url);
		Assert.assertEquals(brows.getUrl(), url);
		brows.loadBrowser();
	}
	@Test
	public void loadBrowserTest() throws IOException
	{
		System.out.println("Dummy Test");
		brows.initiateSearch();
		brows.closeBrowser();
	}
}
