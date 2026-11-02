package com.example.data.model

import java.util.LinkedList
import kotlin.random.Random

data class UsaPersonName(
    val firstName: String,
    val lastName: String
) {
    val fullName: String get() = "$firstName $lastName"
}

object UsaNameGenerator {

    val MALE_FIRST_NAMES = listOf(
        "James", "Robert", "John", "Michael", "David", "William", "Richard", "Joseph",
        "Thomas", "Christopher", "Charles", "Daniel", "Matthew", "Anthony", "Mark",
        "Donald", "Steven", "Andrew", "Paul", "Joshua", "Kenneth", "Kevin", "Brian",
        "George", "Timothy", "Ronald", "Edward", "Jason", "Jeffrey", "Ryan", "Jacob",
        "Gary", "Nicholas", "Eric", "Jonathan", "Stephen", "Larry", "Justin", "Scott",
        "Brandon", "Benjamin", "Samuel", "Gregory", "Alexander", "Patrick", "Frank",
        "Raymond", "Jack", "Dennis", "Jerry", "Tyler", "Aaron", "Jose", "Adam", "Nathan",
        "Henry", "Zachary", "Douglas", "Peter", "Kyle", "Noah", "Ethan", "Jeremy",
        "Christian", "Walter", "Keith", "Austin", "Roger", "Terry", "Sean", "Gerald",
        "Carl", "Harold", "Dylan", "Arthur", "Lawrence", "Jordan", "Jesse", "Bryan",
        "Billy", "Bruce", "Gabriel", "Joe", "Logan", "Alan", "Juan", "Albert", "Willie",
        "Elijah", "Wayne", "Randy", "Vincent", "Mason", "Roy", "Ralph", "Bobby", "Russell",
        "Bradley", "Philip", "Eugene", "Shawn", "Travis", "Barry", "Lucas", "Caleb",
        "Hunter", "Jackson", "Luke", "Oliver", "Liam", "Wyatt", "Carter", "Julian",
        "Levi", "Isaac", "Owen", "Landon", "Connor", "Eli", "Nolan", "Cameron", "Adrian",
        "Colton", "Easton", "Axel", "Carson", "Leo", "Ian", "Dominic", "Gavin", "Asher",
        "Josiah", "Ezra", "Roman", "Theodore", "Ezekiel", "Jeremiah", "Parker", "Miles",
        "Greyson", "Weston", "Jaxson", "Micah", "Silas", "Bennett", "Declan", "Waylon",
        "Wesley", "Everett", "Hudson", "Lincoln", "Cooper", "Brody", "Sawyer", "Bentley",
        "Ryker", "Jace", "Brantley", "Kaiden", "Brayden", "Hayden", "Gage", "Tristan",
        "Chase", "Cole", "Trevor", "Colby", "Garrett", "Spencer", "Tanner", "Dalton",
        "Grant", "Preston", "Dustin", "Cody", "Shane", "Corey", "Derrick",
        "Brett", "Marcus", "Malcolm", "Troy", "Bryce", "Blake", "Devin", "Mitchell",
        "Colin", "Damian", "Curtis", "Clint", "Dean", "Lance", "Wade"
    ).distinct()

    val SURNAMES = listOf(
        "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis",
        "Rodriguez", "Martinez", "Hernandez", "Lopez", "Gonzalez", "Wilson", "Anderson",
        "Thomas", "Taylor", "Moore", "Jackson", "Martin", "Lee", "Perez", "Thompson",
        "White", "Harris", "Sanchez", "Clark", "Ramirez", "Lewis", "Robinson", "Walker",
        "Young", "Allen", "King", "Wright", "Scott", "Torres", "Nguyen", "Hill",
        "Flores", "Green", "Adams", "Nelson", "Baker", "Hall", "Rivera", "Campbell",
        "Mitchell", "Carter", "Roberts", "Gomez", "Phillips", "Evans", "Turner", "Diaz",
        "Parker", "Cruz", "Edwards", "Collins", "Reyes", "Stewart", "Morris", "Morales",
        "Murphy", "Cook", "Rogers", "Gutierrez", "Ortiz", "Morgan", "Cooper", "Peterson",
        "Bailey", "Reed", "Kelly", "Howard", "Ramos", "Kim", "Cox", "Ward", "Richardson",
        "Watson", "Brooks", "Chavez", "Wood", "James", "Bennett", "Gray", "Mendoza",
        "Ruiz", "Hughes", "Price", "Alvarez", "Castillo", "Sanders", "Patel", "Myers",
        "Long", "Ross", "Foster", "Jimenez", "Powell", "Jenkins", "Perry", "Russell",
        "Sullivan", "Bell", "Coleman", "Butler", "Henderson", "Barnes", "Gonzales",
        "Fisher", "Vasquez", "Simmons", "Romero", "Jordan", "Patterson", "Alexander",
        "Hamilton", "Graham", "Reynolds", "Griffin", "Wallace", "West", "Cole",
        "Hayes", "Bryant", "Herrera", "Gibson", "Ellis", "Tran", "Medina", "Aguilar",
        "Stevens", "Murray", "Ford", "Castro", "Marshall", "Owens", "Harrison",
        "Fernandez", "McDonald", "Woods", "Washington", "Kennedy", "Wells", "Vargas",
        "Henry", "Chen", "Freeman", "Webb", "Tucker", "Guzman", "Burns", "Crawford",
        "Olson", "Simpson", "Porter", "Hunter", "Gordon", "Mendez", "Silva", "Shaw",
        "Snyder", "Mason", "Dixon", "Hunt", "Hicks", "Holmes", "Palmer",
        "Wagner", "Black", "Robertson", "Boyd", "Rose", "Stone", "Salazar", "Fox",
        "Warren", "Mills", "Meyer", "Rice", "Schmidt", "Garza", "Daniels", "Ferguson",
        "Nichols", "Stephens", "Soto", "Weaver", "Ryan", "Gardner", "Payne", "Grant"
    ).distinct()

    private val firstNameDeck = ArrayDeque<String>()
    private val lastNameDeck = ArrayDeque<String>()
    private var lastPickedFirst: String = ""
    private var lastPickedLast: String = ""

    private val history = LinkedList<UsaPersonName>()
    private var historyIndex = -1
    private const val MAX_HISTORY = 50

    @Synchronized
    private fun drawUniqueFirstName(): String {
        if (firstNameDeck.isEmpty()) {
            val shuffled = MALE_FIRST_NAMES.shuffled(Random(System.nanoTime()))
            firstNameDeck.addAll(shuffled)
            // Ensure first of new deck doesn't match last picked
            if (firstNameDeck.size > 1 && firstNameDeck.first() == lastPickedFirst) {
                val first = firstNameDeck.removeFirst()
                firstNameDeck.addLast(first)
            }
        }
        val next = firstNameDeck.removeFirst()
        lastPickedFirst = next
        return next
    }

    @Synchronized
    private fun drawUniqueLastName(): String {
        if (lastNameDeck.isEmpty()) {
            val shuffled = SURNAMES.shuffled(Random(System.nanoTime()))
            lastNameDeck.addAll(shuffled)
            if (lastNameDeck.size > 1 && lastNameDeck.first() == lastPickedLast) {
                val first = lastNameDeck.removeFirst()
                lastNameDeck.addLast(first)
            }
        }
        val next = lastNameDeck.removeFirst()
        lastPickedLast = next
        return next
    }

    @Synchronized
    fun nextRandomFirstName(): String {
        val nextFirst = drawUniqueFirstName()
        val currentLast = if (history.isNotEmpty() && historyIndex in history.indices) {
            history[historyIndex].lastName
        } else {
            drawUniqueLastName()
        }
        val updated = UsaPersonName(nextFirst, currentLast)
        recordInHistory(updated)
        return nextFirst
    }

    @Synchronized
    fun nextRandomLastName(): String {
        val currentFirst = if (history.isNotEmpty() && historyIndex in history.indices) {
            history[historyIndex].firstName
        } else {
            drawUniqueFirstName()
        }
        val nextLast = drawUniqueLastName()
        val updated = UsaPersonName(currentFirst, nextLast)
        recordInHistory(updated)
        return nextLast
    }

    @Synchronized
    fun nextRandomName(): UsaPersonName {
        val first = drawUniqueFirstName()
        val last = drawUniqueLastName()
        val person = UsaPersonName(first, last)
        recordInHistory(person)
        return person
    }

    private fun recordInHistory(person: UsaPersonName) {
        while (history.size > historyIndex + 1 && historyIndex >= 0) {
            history.removeLast()
        }
        history.add(person)
        if (history.size > MAX_HISTORY) {
            history.removeFirst()
        }
        historyIndex = history.size - 1
    }

    @Synchronized
    fun currentName(): UsaPersonName {
        return if (history.isNotEmpty() && historyIndex in history.indices) {
            history[historyIndex]
        } else {
            nextRandomName()
        }
    }

    @Synchronized
    fun previousName(): UsaPersonName? {
        if (historyIndex > 0) {
            historyIndex--
            return history[historyIndex]
        }
        return null
    }

    @Synchronized
    fun hasPrevious(): Boolean = historyIndex > 0

    @Synchronized
    fun hasNextInHistory(): Boolean = historyIndex < history.size - 1

    @Synchronized
    fun nextInHistory(): UsaPersonName? {
        if (hasNextInHistory()) {
            historyIndex++
            return history[historyIndex]
        }
        return null
    }
}
