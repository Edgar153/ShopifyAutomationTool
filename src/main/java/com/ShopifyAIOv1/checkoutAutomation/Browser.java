package com.ShopifyAIOv1.checkoutAutomation;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

public class Browser implements BasicSiteFunctions, Checkout
{
	public class PageRefresherFix
	{
		public void run() {}
	}
	public final static int productPerPage = 30;
	public int threadID = 0;
	private final int availableThreads = Runtime.getRuntime().availableProcessors();
	private final static String extension = "/products.json?page=1&limit=250";
	private final static HashMap<String, String[]> webMap = new HashMap<String, String[]>();
	private final static ObjectMapper mapper = new ObjectMapper();
	private static JsonNode jsonObject;

	private ArrayList<String> keywords = new ArrayList<String>();
	private String url;
	private String variant;
	private String sizeOption = null;
	/*----------------Shared Variables between Threads-----------------*/
	// -----------------------------------------------------------------//
	public static volatile int bestPage = Integer.MAX_VALUE;
	public static volatile int globalMax = Integer.MIN_VALUE;
	private static int tasks;
	private static volatile int notifier = 0;
	// ------------------------------------------------------------------//
	/*-----------------------------------------------------------------*/
	private int[] priceRange = {Integer.MIN_VALUE, Integer.MAX_VALUE};
	private Object[] bestProduct;
	private boolean validVariant = false;
	private Thread[] thread = new Thread[20];
	long start, end;

	public static String[] sites;
	public Browser()
	{
		// The Following websites contain a products.json file, which allows for
		// easier monitoring
		sites = defaultSites();
		mapDefaultSites();
	}
	// Maps the total page count with limit = 250 products per page for each
	// supported Shoppify Website.
	/*
	 * Total Pages:(as of 1/12/2020): Kith = 270, Undefeated = 36 ExtraButterNY
	 * = 65 BodegaStore = 414 Cncpts = 51 NotreShop = 28 JuiceStore = 28
	 */
	public void mapDefaultSites()
	{

		webMap.put("kith", new String[]
		{sites[0], "270", Integer.toString(calcPage250(270))});
		webMap.put("undefeated", new String[]
		{sites[1], "36", Integer.toString(calcPage250(36))});
		webMap.put("extrabutterny", new String[]
		{sites[2], "65", Integer.toString(calcPage250(65))});
		webMap.put("bodegastore", new String[]
		{sites[3], "414", Integer.toString(calcPage250(414))});
		webMap.put("cncpts", new String[]
		{sites[4], "51", Integer.toString(calcPage250(51))});
		webMap.put("notreshop", new String[]
		{sites[5], "28", Integer.toString(calcPage250(28))});
		webMap.put("juicestrore", new String[]
		{sites[6], "28", Integer.toString(calcPage250(28))});

	}
	public String getSites()
	{
		String str = "";
		for (String s : webMap.keySet())
			// removes the characters "[" "]" "," from the Arrays.toString()
			// method
			str = str + Arrays.toString(webMap.get(s)).replaceAll("[\\[\\],]", "") + "\n";
		return str;
	}
	private int calcPage250(int pages)
	{
		// Math.Ceiling implementation to round to the next highest number. i.e.
		// (x + (n-1))/n
		return ((pages * 30) + 249) / 250;
	}
	public void initiateSearch() throws IOException, InterruptedException
	{
		// ---------------------------Create Threads for faster searching------------------------------------------//
		Runnable runnable = new Runnable()
		{
			public void run()
			{
				try
				{
					int threadNum = threadID;
					String data = getUrl() + extension.replace("page=1",
							"page=" + thread[threadNum].getName()
									.replace("Thread ", ""));
					String JSON = fetchJSONData(data);
					jsonObject = mapper.readTree(JSON);
					int tmpProductCounter = jsonObject.get("products").size();
					System.out.println(tmpProductCounter);
					productSearch(tmpProductCounter, threadNum);
				} catch (IOException | InterruptedException e)
				{
					e.printStackTrace();
				}
			}
		};
		for (int i = 0; i < (tasks = ((availableThreads > 20) ? 20: availableThreads)); i++)
		{
			thread[i] = new Thread(runnable, ("Thread " + (2 + i)));
			thread[i].start();
			Thread.sleep(1);
			threadID++;
			System.out.println(thread[i].getName() + " has been initiated.");
		}
		String JSON = fetchJSONData(url);
		// Format the JSON Data
		mapper.enable(SerializationFeature.INDENT_OUTPUT);
		jsonObject = mapper.readTree(JSON);
		/*------------------For Data Extraction Analysis----------------------*/
		// String formattedJson =
		// mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObject);
		// BufferedWriter writer = new BufferedWriter(new
		// FileWriter("json.json"));
		// writer.write(formattedJson);
		// writer.close();
		/*------------------For Data Extraction Analysis----------------------*/
		// Must follow this format in order to access variants of each product
		// without creating
		// more JsonNode instances:
		// jsonObject.get("products").get(i).get("variants").toString()
		// where i is the i-th product.
		int tmpProductCounter = jsonObject.get("products").size();
		System.out.println(tmpProductCounter);
		productSearch(tmpProductCounter, -1);
	}
	private void productSearch(int productNum, int threadNum)
			throws IOException, InterruptedException
	{
		int productNumber = 0;
		int max = Integer.MIN_VALUE;
		int[] productIndex = new int[productNum];
		String handle, tags,vendor, title;
		if (threadNum == -1)
			start = System.currentTimeMillis();
		for (JsonNode product : jsonObject.get("products"))
		{
			int count = 0;
			int price = (int) Double.parseDouble(
					product.get("variants").get(0).get("price").asText());
			if (price >= priceRange[0] && price <= priceRange[1])
			{
				title = product.get("title").toString().toLowerCase();
				handle = product.get("handle").toString().toLowerCase();
				tags = product.get("tags").toString().toLowerCase();
				vendor = product.get("vendor").toString().toLowerCase();
				String keywordList = title + handle + tags + vendor;
				for (String keyword : keywords)
				{	
					if(keyword == null)
						break;
					else if (keywordList.contains(keyword.toLowerCase()) && keyword != "")
						count++;
					if (max < count)
						max = count;
				}
			}
			productIndex[productNumber] = count;
			productNumber++;
		}
		Multimap<Integer, Integer> products = ArrayListMultimap.create();
		for (int i = 0; i < productIndex.length; i++)
			products.put(productIndex[i], i);
		System.out.println(products.toString());
		Collection<Integer> maxMatches = products.get(max);
		Object[] matches = maxMatches.toArray();
		synchronized (this)
		{
			int currPage = (threadNum != -1) ?  Integer.parseInt(thread[threadNum].getName().replace("Thread ", "")) : 1;
			if (globalMax <= max && currPage < bestPage)
			{
				globalMax = max;
				bestProduct = matches;
				bestPage = currPage;
				System.out.println("BestPage is now " + bestPage);
				System.out.println("Total Matches: " + max);
			}
			if (threadNum != -1)
			{
				System.out.println(thread[threadNum].getName() + " has finished processing products.");
				notifier++;
				this.notify();
				return;
			}
			while(notifier != tasks)
				this.wait();
		}
		String newUrl = getUrl() + extension.replace("page=1", "page=" + bestPage);
		setUrl(newUrl);
		if(bestProduct.length == 0)
			System.out.println("No Product was found. Terminating...");
		else
			findVariantID(bestProduct, bestProduct.length);
		end = System.currentTimeMillis();
		System.out.println("Page Search Performance Time: " + (end - start) + " ms");

	}
	private void findVariantID(Object[] matches, int length)throws JsonMappingException, JsonProcessingException
	{
		if (sizeOption == null)
		{
			String JSON = fetchJSONData(url);
			jsonObject = mapper.readTree(JSON);
			String str = jsonObject.get("products")
					.get(Integer.parseInt(matches[0].toString()))
					.get("variants").get(0).get("id").toString();
			setVariant(str);
		} 
	}
	public void setUrl(String url)
	{
		this.url = url + extension;
	}
	public String getUrl()
	{
		return url.substring(0, url.indexOf("/products"));
	}
	private int pageCount()
	{
		long start = System.currentTimeMillis();
		int counter = 9;
		int nextPage = 10;
		String appendPages = "?page=";
		String newUrl = url + appendPages + nextPage;
		String emptyProductObject = "{\"products\":[]}";
		String JSON = fetchJSONData(newUrl);
		// Binary Search
		while (JSON != emptyProductObject)
		{
			counter++;
			newUrl = url + appendPages + nextPage;
			JSON = fetchJSONData(newUrl);
		}
		long end = System.currentTimeMillis();
		System.out.println("Page Count Performance Time: " + (end - start));
		return counter;
	}
	private String fetchJSONData(String url)
	{
		String data = "";
		try
		{
			String str;
			URL site = new URL(url);
			BufferedReader br = new BufferedReader(
					new InputStreamReader(site.openStream()));
			while (null != (str = br.readLine()))
				data += str;
		} catch (Exception ex)
		{
			ex.printStackTrace();
		}
		return data;
	}
	public void setTotalPages(String pages, String key)
	{
		setTotalPagesImplementation(pages, key);
	}
	private void setTotalPagesImplementation(String pages, String key)
	{
		String[] str = Arrays.toString(webMap.get(key))
				.replaceAll("[\\[\\]]", "").split(",");
		str[1] = pages;
		str[2] = Integer.toString(calcPage250(Integer.parseInt(str[1])));
		webMap.replace(key, str);
	}
	private void loadCheckoutVariantImplementation(clientInfo info) throws InterruptedException
	{
		Proxies prox = new Proxies();
		int delay = 150;
		String checkoutURL = getUrl() + checkoutExtension + variant;
		Runnable multiCheckout = new Runnable()
		{
			@Override
			public void run() 
			{
				WebDriver threadDriver;
				try 
				{
					synchronized(this)
					{
						threadDriver = prox.createDriver();
					}
					WebDriverWait wait = new WebDriverWait(threadDriver, 30);
					threadDriver.get(checkoutURL);
					wait.until(driver -> ((JavascriptExecutor) threadDriver).executeScript("return document.readyState").equals("complete"));
					WebElement t = threadDriver.findElement(By.xpath("//main[@role='main']"));
					t.findElement(By.name("checkout")).click();
					wait.until(driver -> ((JavascriptExecutor) threadDriver).executeScript("return document.readyState").equals("complete"));
					PageRefresherFix tryAgain = new PageRefresherFix() {
						@Override
						public void run()
						{
							try 
							{
								WebElement r = threadDriver.findElement(By.xpath("//main[@role='main']"));
								r.findElement(By.name("checkout")).click();
								wait.until(driver -> ((JavascriptExecutor) threadDriver).executeScript("return document.readyState").equals("complete"));
							}
							catch(StaleElementReferenceException e)
							{
								System.out.println("Stale Element Exception caught... Trying Again");
								threadDriver.navigate().refresh();
								wait.until(driver -> ((JavascriptExecutor) threadDriver).executeScript("return document.readyState").equals("complete"));
								run();
							}				
						}
					};
					if(threadDriver.getTitle().toLowerCase().contains("cart"))
							tryAgain.run();
					String temp = getUrl().substring(getUrl().indexOf("//")+2, getUrl().indexOf("."));
					switch (temp)
					{
						case "undefeated":
							threadDriver.findElement(By.xpath("//input[@name='customer[email]']")).sendKeys(info.getLoginEmail());
							Thread.sleep(delay);
							threadDriver.findElement(By.xpath("//input[@type='password']")).sendKeys(info.getLoginPassword());
							Thread.sleep(delay);
							threadDriver.findElement(By.xpath("//input[@type='submit']")).click();
							wait.until(driver -> ((JavascriptExecutor) threadDriver).executeScript("return document.readyState").equals("complete"));
							break;
						default:
							try
							{
								t = threadDriver.findElement(By.xpath("//div[@data-step='stock_problems']"));
								System.out.println("Error: Item is Out of Stock.");
								threadDriver.quit();
								return;


							}catch(NoSuchElementException e)
							{
							}
					}
					threadDriver.findElement(By.xpath("//input[@type='email']")).sendKeys(info.getEmail());
					Thread.sleep(delay);
					threadDriver.findElement(By.xpath("//input[@name='checkout[shipping_address][first_name]']")).sendKeys(info.getFName());
					Thread.sleep(delay);
					threadDriver.findElement(By.xpath("//input[@name='checkout[shipping_address][last_name]']")).sendKeys(info.getLName());
					Thread.sleep(delay);
					threadDriver.findElement(By.xpath("//input[@name='checkout[shipping_address][address1]']")).sendKeys(info.getAddress());
					Thread.sleep(delay);
					threadDriver.findElement(By.xpath("//input[@name='checkout[shipping_address][address2]']")).sendKeys(info.getAddress2());
					Thread.sleep(delay);
					threadDriver.findElement(By.xpath("//input[@name='checkout[shipping_address][city]']")).sendKeys(info.getCity());
					Thread.sleep(delay);
					threadDriver.findElement(By.xpath("//input[@name='checkout[shipping_address][country]']")).sendKeys(info.getCountry_Region());
					Thread.sleep(delay);
					threadDriver.findElement(By.xpath("//input[@name='checkout[shipping_address][province]']")).sendKeys(info.getState());
					Thread.sleep(delay);
					threadDriver.findElement(By.xpath("//input[@name='checkout[shipping_address][zip]']")).sendKeys(info.getZIP_code());
					Thread.sleep(delay);
					threadDriver.findElement(By.xpath("//input[@name='checkout[shipping_address][phone]']")).sendKeys(info.getPhone());
					threadDriver.findElement(By.xpath("//button[@class='step__footer__continue-btn btn']")).click();
					wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//button[@class='step__footer__continue-btn btn']")));
					threadDriver.findElement(By.xpath("//button[@class='step__footer__continue-btn btn']")).click();
					wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//iframe[@class='card-fields-iframe']")));
					List<WebElement> paymentInfo = threadDriver.findElements(By.xpath("//iframe[@class='card-fields-iframe']"));
					threadDriver.switchTo().frame(paymentInfo.get(0));
					Thread.sleep(600);
					threadDriver.findElement(By.xpath("//input[@autocomplete='cc-number']")).sendKeys(info.getCCNumber());
					Thread.sleep(delay);
					threadDriver.switchTo().parentFrame();
					threadDriver.switchTo().frame(paymentInfo.get(1));
					threadDriver.findElement(By.xpath("//input[@autocomplete='cc-name']")).sendKeys(info.getCCName());
					Thread.sleep(delay);
					threadDriver.switchTo().parentFrame();
					threadDriver.switchTo().frame(paymentInfo.get(2));
					threadDriver.findElement(By.xpath("//input[@autocomplete='cc-exp']")).sendKeys(info.getCCExp());
					Thread.sleep(delay);
					threadDriver.switchTo().parentFrame();
					threadDriver.switchTo().frame(paymentInfo.get(3));
					threadDriver.findElement(By.xpath("//input[@autocomplete='cc-csc']")).sendKeys(info.getCsc());
					Thread.sleep(delay);
					threadDriver.switchTo().parentFrame();
					threadDriver.findElement(By.xpath("//button[@class='step__footer__continue-btn btn']")).click();
					threadDriver.quit();
					System.out.println(Thread.currentThread().getName()+" has finished checkout...");
				}catch(InterruptedException  |TimeoutException  | IOException e)
				{
					System.out.println("Caught Error: "+e);
				}
			}
		};
		for (int i = 0; i < tasks; i++)
		{
			thread[i] = new Thread(multiCheckout, ("Thread " + (2 + i)));
			thread[i].start();
			Thread.sleep(1);
			threadID++;
			System.out.println(thread[i].getName() + " has been initiated for checkout.");
		}
		for(Thread s : thread)
			s.join();
	}
	public void loadCheckoutVariant(clientInfo info) throws InterruptedException
	{
		loadCheckoutVariantImplementation(info);
	}
	public void setVariant(String variant)
	{
		this.variant = variant;
		this.validVariant = true;
	}
	public String getVariant()
	{
		return variant;
	}
	public String getKeywords()
	{
		return keywords.toString();
	}
	public void setKeywords(String[] keywords)
	{
		this.keywords.clear();
		this.keywords.addAll(Arrays.asList(keywords));
	}
	public String getSizeOption()
	{
		return sizeOption;
	}
	public void setSizeOption(String sizeOption)
	{
		this.sizeOption = sizeOption.toLowerCase();
	}
	public boolean isValidVariant()
	{
		return validVariant;
	}
	public int[] getPriceRange()
	{
		return priceRange;
	}
	public void setPriceRange(int[] priceRange)
	{
		this.priceRange = priceRange;
	}
	public static String[] defaultStores()
	{
		return new String[] {"Kith", "Undefeated", "ExtraButteryNY", "Bodega Store", "Cncpts",
							"Notre Shop", "Juice Store", "Haven Shop"};
	}
	public static String[] defaultSites()
	{
		return new String[]
		{		"https://kith.com/collections/all/products.json",
				"https://undefeated.com/collections/all/products.json",
				"https://shop.extrabutterny.com/collections/all/products.json",
				"https://bdgastore.com/collections/all/products.json",
				"https://cncpts.com/collections/all/products.json",
				"https://www.notre-shop.com/collections/all/products.json",
				"https://juicestore.com/collections/all/products.json",
				"https://shop.havenshop.com/collections/all/products.json"};
	}
}