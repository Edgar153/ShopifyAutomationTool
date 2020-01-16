import java.util.ArrayList;

public interface BasicSiteFunctions
{
	public String getUrl();
	public String getSites();
	public void setUrl(String url);
	public void loadBrowser();
	public void mapDefaultSites();
	public void setTotalPages(String pages, String key);
	public String getKeywords();
	public void setKeywords(String[] keywords);
}