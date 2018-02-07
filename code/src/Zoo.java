import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Zoo{
  private ConcurrentHashMap<Animal, Integer> countMap;
  private Lock zooLock = new ReentrantLock();
  
  public Zoo(){
    countMap = new ConcurrentHashMap<>();
  }
   
  public int getAnimalCount(Animal animal){
    if(animal == null)
      return -1;
    
    zooLock.lock();
    int count;
    try{
    	count = countMap.getOrDefault(animal, 0);
    } finally{
      	zooLock.unlock();
    }
    return count;
  }
  
  public int addAnimalCount(Animal animal, int addedCount){
    if(animal == null)
    return -1;
    
    zooLock.lock();
    int count;
    try{
      	count = countMap.getOrDefault(animal, 0) + addedCount;
    	countMap.put(animal, count);
    } finally{
      	zooLock.unlock();
    }
    return count;
  }
  
  public int removeAnimalCount(Animal animal, int removeCount){
    if(animal == null)
      return -1;
    
    zooLock.lock();
    int count;
    try{
        count = countMap.getOrDefault(animal, 0) - removeCount;
      	if(count < 0)
          countMap.remove(animal);
          
    	countMap.put(animal, count);
    } finally{
      	zooLock.unlock();
    }
    return count;
  }
}
