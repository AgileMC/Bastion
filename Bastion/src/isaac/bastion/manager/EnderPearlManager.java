package isaac.bastion.manager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.untamedears.humbug.CustomNMSEntityEnderPearl;

import isaac.bastion.Bastion;
import isaac.bastion.BastionBlock;
import isaac.bastion.storage.BastionBlockSet;

public class EnderPearlManager {
	public static final int MAX_TELEPORT=800;
	private BastionBlockSet bastions;
	private Map<EnderPearl,Integer> endTimes;
	private Map<EnderPearl,BastionBlock> blocks;
	public EnderPearlManager(){
		bastions=Bastion.getBastionManager().bastions;

		endTimes=new HashMap<EnderPearl,Integer>();
		blocks=new HashMap<EnderPearl,BastionBlock>();
	}
	public void handlePearlLaunched(EnderPearl pearl){
		getBlocking(pearl);
	}
	private void getBlocking(EnderPearl pearl){
		double gravity=0.03F;

		if(pearl instanceof CustomNMSEntityEnderPearl)
			gravity=((CustomNMSEntityEnderPearl)pearl).y_adjust_;

		//Bastion.getPlugin().getLogger().info("gravity="+gravity);
		Vector speed=pearl.getVelocity();
		Vector twoDSpeed=speed.clone();
		twoDSpeed.setY(0);

		double horizontalSpeed=twoDSpeed.length();
		double verticalSpeed=speed.getY();

		Location loc=pearl.getLocation();

		double maxTicks=getMaxTicks(verticalSpeed,loc.getY(),-gravity);
		double maxDistance=getMaxDistance(horizontalSpeed,maxTicks);


		//check if it has any possibility of going through a bastion 
		if(maxDistance<2){
			return;
		}

		LivingEntity threwE=pearl.getShooter();
		Player threw=null;
		String playerName=null;
		if(threwE instanceof Player){
			threw=(Player) threwE;
			playerName=threw.getName();
		}

		Set<BastionBlock> possible=bastions.getPossibleTeleportBlocking(pearl.getLocation(),playerName);

		//no need to do anything if there aren't any bastions to run into.
		if(possible.isEmpty()){

			return;
		}

		Location start=pearl.getLocation();
		Location end=start.clone();
		end.add(twoDSpeed.multiply(maxTicks));

		Set<BastionBlock> couldCollide=simpleCollide(possible,start.clone(),end.clone());

		if(couldCollide.isEmpty()){
			return;
		}


		BastionBlock firstCollision=null;
		double collidesBy=-1;
		for(BastionBlock bastion : couldCollide){
			double currentCollidesBy=collidesBy(bastion, start.clone(), end.clone(), speed, gravity, horizontalSpeed);
			if(currentCollidesBy!=-1&&currentCollidesBy<collidesBy){
				collidesBy=currentCollidesBy;
				firstCollision=bastion;
			}
			if(collidesBy==-1&&currentCollidesBy!=-1){
				collidesBy=currentCollidesBy;
				firstCollision=bastion;
			}
		}
		if(collidesBy!=-1){
			Bastion.getPlugin().getLogger().info("adding collision");
			endTimes.put(pearl, (int) collidesBy);
			blocks.put(pearl, firstCollision);
		}


	}
	private Set<BastionBlock> simpleCollide(Set<BastionBlock> possible,Location start,Location end){
		Set<BastionBlock> couldCollide=new TreeSet<BastionBlock>();
		for(BastionBlock bastion : possible){
			Location loc=bastion.getLocation().clone();
			loc.setY(0);
			if(circleLineCollide(start,end,loc,BastionBlock.getRadiusSquared()))
				couldCollide.add(bastion);
		}

		return couldCollide;
	}

	double collidesBy(BastionBlock bastion, Location startLoc,Location endLoc,Vector speed,double gravity,double horizontalSpeed){


		//Get the points were our line crosses the circle
		List<Location>  collision_points=getCollisionPoints(startLoc,endLoc,bastion.getLocation(),BastionBlock.getRadiusSquared());

		//solve the quadratic equation for the equation governing the pearls y height. See if it ever reaches (bastion.getLocation().getY()+1
		List<Double> solutions=getSolutions(-gravity/2,speed.getY(),startLoc.getY()-(bastion.getLocation().getY()+1));
		//Bastion.getPlugin().getLogger().info("solutions="+solutions);
		//If there aren't any results we no there are no intersections
		if(solutions.isEmpty()){
			return -1;
		}
		Location temp=startLoc.clone();
		temp.setY(0);

		double startingLength=temp.toVector().length();
		//Solutions held the time at which the collision would happen lets change it to a position
		for(int i=0;i<solutions.size();++i)
			solutions.set(i, solutions.get(i)*horizontalSpeed+startingLength);

		List<Double> oneDCollisions=new ArrayList<Double>();

		//turn those points into scalers along the line of the pearl
		for(Location collision_point : collision_points){
			oneDCollisions.add(collision_point.toVector().length());
		}


		double result=-1;
		for(Double collisionPoint : oneDCollisions){
			//if this is the solution lets convert it to a tick
			//check if the collision point is inside between the solutions if so we no there will be a collision
			if(solutions.get(0) > collisionPoint && solutions.get(1) < collisionPoint){
				//solution 1 is between the two collision points
				if(oneDCollisions.get(0)>solutions.get(1)&&oneDCollisions.get(1)<solutions.get(0)||
						oneDCollisions.get(1)>solutions.get(1)&&oneDCollisions.get(0)<solutions.get(0)){
					return (solutions.get(1)-startingLength)/horizontalSpeed+startLoc.getWorld().getFullTime();
				} else{
					if(oneDCollisions.get(0)<oneDCollisions.get(1)){
						return (oneDCollisions.get(0)-startingLength)/horizontalSpeed+startLoc.getWorld().getFullTime();
					}else{
						return (oneDCollisions.get(1)-startingLength)/horizontalSpeed+startLoc.getWorld().getFullTime();
					}
				}
			}

		}

		return result;
	}
	private List<Double> getSolutions(double a, double b, double c){
		double toTakeSquareRoot=b*b-4*a*c;
		if(toTakeSquareRoot<0||a==0)
			return Collections.emptyList();

		double squareRooted=Math.sqrt(toTakeSquareRoot);

		double s1=(-b-squareRooted)/(2*a);

		double s2=(-b+squareRooted)/(2*a);

		return  Arrays.asList(s1, s2);
	}
	//Code lifted with some modification from http://stackoverflow.com/a/13055116 a answer by arne-b this section is licensed under http://creativecommons.org/licenses/by-sa/3.0/ 
	private List<Location> getCollisionPoints(Location startLoc,Location endLoc,Location circleLoc,double radiusSquared){
		double baX = endLoc.getX() - startLoc.getX();
		double baY = endLoc.getZ() - startLoc.getZ();
		double caX = circleLoc.getX() - startLoc.getX();
		double caY = circleLoc.getZ() - startLoc.getZ();

		double a = baX * baX + baY * baY;
		double bBy2 = baX * caX + baY * caY;
		double c = caX * caX + caY * caY - radiusSquared;

		double pBy2 = bBy2 / a;
		double q = c / a;

		double disc = pBy2 * pBy2 - q;
		if (disc < 0) {
			return Collections.emptyList();
		}
		// if disc == 0 ... dealt with later
		double tmpSqrt = Math.sqrt(disc);
		double abScalingFactor1 = -pBy2 + tmpSqrt;
		double abScalingFactor2 = -pBy2 - tmpSqrt;

		Location p1 = new Location(startLoc.getWorld(), startLoc.getX() - baX * abScalingFactor1,0, startLoc.getZ()
				- baY * abScalingFactor1);
		if (disc == 0) { // abScalingFactor1 == abScalingFactor2
			return Collections.singletonList(p1);
		}
		Location p2 = new Location(startLoc.getWorld(),startLoc.getX() - baX * abScalingFactor2,0, startLoc.getZ()
				- baY * abScalingFactor2);
		return Arrays.asList(p1, p2);
	}
	private double getMaxDistance(double horizontalSpeed,double maxTicks){
		return horizontalSpeed*maxTicks;
	}

	private double getMaxTicks(double verticalSpeed,double y,double deltaY){
		return ((-verticalSpeed)-Math.sqrt(verticalSpeed*verticalSpeed-2*deltaY*y))/deltaY;
	}

	private boolean circleLineCollide(Location startLoc, Location endLoc, Location circleLoc, double radiusSquared){
		Location lineStart=startLoc.clone();
		Location lineEnd=endLoc.clone();
		Location circleCenter=circleLoc.clone();

		Vector direction=vectorFromLocations(lineStart,lineEnd);

		if(direction.getZ()==0){
			if(direction.getBlockX()==0){
				return false;
			}
			flipXZ(lineStart);
			flipXZ(lineEnd);
			flipXZ(circleCenter);
			flipXZ(direction);
		}

		Vector start=lineStart.toVector();
		Vector end=lineEnd.toVector();

		Vector circle=circleCenter.toVector();

		double slope=direction.getZ()/direction.getX();
		double perpSlope=-1/slope;

		//This is the closest x if this line segment was extended for ever
		double closestX=(slope*start.getX()-perpSlope*circle.getX()+circle.getBlockZ()-start.getZ())/
				(slope-perpSlope);

		//Getting the Z from the x is easy
		double closestZ=slope*(closestX-start.getX())+start.getZ();

		Vector closest=new Vector(closestX,0,closestZ);

		double distanceSquared=closest.clone().subtract(circle).lengthSquared();

		if(distanceSquared>radiusSquared){
			return false;
		}

		if(((closest.getX()>lineStart.getX()&&closest.getX()>lineEnd.getX())||
				(closest.getZ()>lineStart.getZ()&&closest.getZ()>lineEnd.getZ()))||

				((closest.getX()<lineStart.getX()&&closest.getX()<lineEnd.getX())||
						(closest.getZ()<lineStart.getZ()&&closest.getZ()<lineEnd.getZ()))
				){
			if(closest.clone().subtract(end).lengthSquared()<closest.clone().subtract(start).lengthSquared()){
				closest=end;
			} else{
				closest=start;
			}
		}

		distanceSquared=closest.subtract(circle).lengthSquared();

		if(distanceSquared>radiusSquared){
			return false;
		}

		return true;
	}
	private void flipXZ(Location a){
		double tempX=a.getX();
		a.setX(a.getZ());
		a.setZ(tempX);
	}

	private void flipXZ(Vector a){
		double tempX=a.getX();
		a.setX(a.getZ());
		a.setZ(tempX);
	}

	private Vector vectorFromLocations(Location start, Location end){
		return new Vector(end.getX()-start.getX(),end.getY()-start.getY(),end.getZ()-start.getZ());
	}


	public void tick(){
		for(World world : Bastion.getPlugin().getServer().getWorlds()){
			worldTick(world);
		}
	}
	private void worldTick(World world){
		Collection<EnderPearl> flying=world.getEntitiesByClass(EnderPearl.class);
		for(EnderPearl pearl : flying){
			pearlTick(pearl);
		}
	}
	private void pearlTick(EnderPearl pearl){
		Integer endTime=endTimes.get(pearl);
		if(endTime==null)
			return;
		Bastion.getPlugin().getLogger().info("endTimes.get(pearl)="+endTime+" Time="+pearl.getWorld().getFullTime());


		if(pearl.getWorld().getFullTime()>endTime){
			String playerName=null;
			Player player=null;
			if(pearl.getShooter() instanceof Player){
				player=(Player)pearl.getShooter();
				playerName=player.getName();
			}

			BastionBlock bastion=blocks.get(pearl);
			if(bastion!=null)
				if(bastion.enderPearlBlocked(pearl.getLocation(), playerName)){
					bastion.handleTeleport(pearl.getLocation(), player);
					pearl.remove();
					blocks.remove(pearl);
					endTimes.remove(pearl);
				}
		}
	}


}