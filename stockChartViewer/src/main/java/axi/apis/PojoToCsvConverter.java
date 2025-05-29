package axi.apis;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.opencsv.CSVWriter;

public class PojoToCsvConverter {

    public static void main(String[] args) throws IllegalArgumentException, IllegalAccessException {
        // Example usage
        List<MyPojo> pojos = Arrays.asList(
            new MyPojo("John", "Sh", new Date(), 30),
            new MyPojo("Jane", "Jo", new Date(), 25)
        );

        String outputPath = "output.csv";
        try {
            writePojosToCsvFile(pojos, outputPath);
            System.out.println("Conversion completed successfully.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static <T> void writePojosToCsvFile(List<T> pojos, String outputPath) throws IOException, IllegalArgumentException, IllegalAccessException {
        try (
        		CSVWriter writer = new CSVWriter(new FileWriter(outputPath, true), ';', '"', '\\', "\n");
        	) {
            // Get the headers from the first object
            if (!pojos.isEmpty()) {
            	T firstPojo = pojos.get(0);
                Field[] fields = firstPojo.getClass().getDeclaredFields();
                String[] headers = Arrays.stream(fields).map(Field::getName).toArray(String[]::new);
                //writer.writeNext(headers);

                // Write the data
                for (T pojo : pojos) {
                    String[] values = new String[fields.length];
                    for (int i = 0; i < fields.length; i++) {
                        Field field = fields[i];
                        field.setAccessible(true);
                        
                        if(field.get(pojo)!=null) {
                        	values[i] = field.get(pojo).toString();
                        } else {
                        	values[i] = "";
                        }
                    }
                    writer.writeNext(values);
                }
            } else {
                throw new IllegalArgumentException("List is empty");
            }
        }
    }
    
    // Example POJO class
    public static class MyPojo {
        private String name;
        private String  surname;
        private Date data;
        private int age;

        public MyPojo(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public MyPojo(String name, String surname, int age) {
			super();
			this.name = name;
			this.surname = surname;
			this.age = age;
		}
        

		public MyPojo(String name, String surname, Date data, int age) {
			super();
			this.name = name;
			this.surname = surname;
			this.data = data;
			this.age = age;
		}

		// Getters and setters (required for field access)
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }

		public String getSurname() {
			return surname;
		}

		public void setSurname(String surname) {
			this.surname = surname;
		}

		public Date getData() {
			return data;
		}

		public void setData(Date data) {
			this.data = data;
		}
        
    }
}



