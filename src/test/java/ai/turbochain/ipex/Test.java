package ai.turbochain.ipex;

import com.alibaba.fastjson.JSON;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author cmai
 * @date 2019/6/14 
 */
@SuppressWarnings("ALL")
public class Test {
    public static  List<Dog> dogs = null;
    static {
            dogs = new ArrayList<Dog>(){
            {
            //    add(new Dog("黄一",11));
           //     add(new Dog("黄一",22));
          //      add(new Dog("黄三",33));
            }
        };

    }
    @SuppressWarnings("AlibabaAvoidManuallyCreateThread")
    public static void main(String[] args) {

        List<String> list =dogs.stream().
                collect(Collectors.groupingBy(dog->dog.getName(),Collectors.counting()))
                .entrySet().stream()
                .filter(entry->entry.getValue()>1)
                .map(entry->entry.getKey())
                .collect(Collectors.toList());
        System.out.println(list.toString());
//        Test.dogs.forEach(dog-> System.out.println("Lambda1:"+dog.getName()));
//
//        Test.dogs.forEach((dog)-> System.out.println("Lambda2:"+dog.getName()));
//
//        //noinspection AlibabaAvoidManuallyCreateThread
//        new Thread(()->{
//            System.out.println("lambda3:"+Thread.currentThread().getName());
//        }).start();
//
//        StringBuffer sb = new StringBuffer();
//        sb.append("1");
//        Runnable runnable=()-> System.out.println("lambda4:"+Thread.currentThread().getName());
//        new Thread(runnable).start();
//
//        Test.dogs.stream()
//                .filter((dog)->dog.getAge()>18)
//                .forEach((dog)-> System.out.println("lambda5:"+dog.getAge()));
//
//        Consumer<Dog> consumer = (dog)-> System.out.println("lambda6:"+dog.getAge());
//        Predicate<Dog> dogPredicate =(dog)->dog.getAge()>18;
//        Test.dogs.stream()
//                .filter(dogPredicate)
//                .limit(1)
//                .forEach(consumer);

    }
    
    class Dog{
    	public String name;
    	public int age;
    	 
    	public Dog(String name,int age) {
    		this.name = name;
    		this.age = age;
    	}
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
    	
    }
}