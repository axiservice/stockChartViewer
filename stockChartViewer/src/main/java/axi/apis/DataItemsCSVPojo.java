package axi.apis;

public class DataItemsCSVPojo {
	String dateTime;
	String name1;
	String val1;
	String name2;
	String val2;
	
	public DataItemsCSVPojo() {
		super();
		// TODO Auto-generated constructor stub
	}
	
	public DataItemsCSVPojo(String dateTime, String name1, String val1, String name2, String val2) {
		super();
		this.dateTime = dateTime;
		this.name1 = name1;
		this.val1 = val1;
		this.name2 = name2;
		this.val2 = val2;
	}

	public String getDateTime() {
		return dateTime;
	}

	public void setDateTime(String dateTime) {
		this.dateTime = dateTime;
	}

	public String getName1() {
		return name1;
	}

	public void setName1(String name1) {
		this.name1 = name1;
	}

	public String getVal1() {
		return val1;
	}

	public void setVal1(String val1) {
		this.val1 = val1;
	}

	public String getName2() {
		return name2;
	}

	public void setName2(String name2) {
		this.name2 = name2;
	}

	public String getVal2() {
		return val2;
	}

	public void setVal2(String val2) {
		this.val2 = val2;
	}

}
