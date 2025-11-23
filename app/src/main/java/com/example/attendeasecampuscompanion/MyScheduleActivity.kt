package com.example.attendeasecampuscompanion

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

class MyScheduleActivity : AppCompatActivity() {
    private lateinit var calendarView: CalendarView
    private lateinit var calendarCard: MaterialCardView
    private lateinit var dateText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyState: LinearLayout
    private lateinit var fabCreateEvent: FloatingActionButton
    private lateinit var viewToggle: SwitchCompat
    private lateinit var weekViewContainer: LinearLayout
    private lateinit var dayHeadersContainer: LinearLayout
    private lateinit var timeColumn: LinearLayout
    private lateinit var weekColumnsContainer: LinearLayout
    private lateinit var btnPrevDate: ImageView
    private lateinit var btnNextDate: ImageView
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val allCourses = mutableListOf<Course>()
    private val allFinalExams = mutableListOf<FinalExam>()
    private val allEvents = mutableListOf<Event>()
    private lateinit var scheduleAdapter: ScheduleAdapter
    private var selectedDate: Calendar = Calendar.getInstance()
    private var userRole: String = "Student"
    private var userCampus: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_schedule)

        supportActionBar?.hide()
        calendarView = findViewById(R.id.calendarView)
        calendarCard = findViewById(R.id.calendarCard)
        dateText = findViewById(R.id.dateText)
        recyclerView = findViewById(R.id.recyclerViewSchedule)
        progressBar = findViewById(R.id.progressBar)
        emptyState = findViewById(R.id.emptyState)
        fabCreateEvent = findViewById(R.id.fabCreateEvent)
        viewToggle = findViewById(R.id.viewToggle)
        weekViewContainer = findViewById(R.id.weekViewContainer)
        dayHeadersContainer = findViewById(R.id.dayHeadersContainer)
        timeColumn = findViewById(R.id.timeColumn)
        weekColumnsContainer = findViewById(R.id.weekColumnsContainer)
        btnPrevDate = findViewById(R.id.btnPrevDate)
        btnNextDate = findViewById(R.id.btnNextDate)

        findViewById<View>(R.id.btnBack).setOnClickListener {
            finish()
        }

        fabCreateEvent.setOnClickListener {
            showEventDialog()
        }

        viewToggle.setOnCheckedChangeListener { _, isChecked ->
            updateDateText()
            if (isChecked) {
                //Week view
                calendarCard.visibility = View.GONE
                recyclerView.visibility = View.GONE
                emptyState.visibility = View.GONE
                weekViewContainer.visibility = View.VISIBLE
                btnPrevDate.visibility = View.VISIBLE
                btnNextDate.visibility = View.VISIBLE
                renderWeekView()
            } else {
                //Month/day view
                calendarCard.visibility = View.VISIBLE
                recyclerView.visibility = View.VISIBLE
                weekViewContainer.visibility = View.GONE
                btnPrevDate.visibility = View.GONE
                btnNextDate.visibility = View.GONE
                filterScheduleByDate()
            }
        }

        btnPrevDate.setOnClickListener {
            navigateDate(-1)
        }

        btnNextDate.setOnClickListener {
            navigateDate(1)
        }
        setupRecyclerView()
        setupCalendar()
        loadScheduleData()
    }

    private fun navigateDate(direction: Int) {
        if (viewToggle.isChecked) {
            selectedDate.add(Calendar.WEEK_OF_YEAR, direction)
        } else {
            selectedDate.add(Calendar.DAY_OF_MONTH, direction)
        }
        calendarView.date = selectedDate.timeInMillis
        
        updateDateText()
        if (viewToggle.isChecked) {
            renderWeekView()
        } else {
            filterScheduleByDate()
        }
    }

    private fun setupRecyclerView() {
        scheduleAdapter = ScheduleAdapter(mutableListOf())
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = scheduleAdapter
    }

    private fun setupCalendar() {
        updateDateText()

        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            selectedDate.set(year, month, dayOfMonth)
            updateDateText()
            if (viewToggle.isChecked) {
                renderWeekView()
            } else {
                filterScheduleByDate()
            }
        }
    }

    private fun updateDateText() {
        if (viewToggle.isChecked) {
             dateText.text = "Week of " + SimpleDateFormat("MMMM d, yyyy", Locale.US).format(selectedDate.time)
        } else {
            val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US)
            dateText.text = dateFormat.format(selectedDate.time)
        }
    }

    private fun loadScheduleData() {
        val currentUserId = auth.currentUser?.uid ?: run {
            handleFailure("No user logged in!")
            return
        }
        progressBar.visibility = View.VISIBLE
        emptyState.visibility = View.GONE
        loadCourses(currentUserId)
    }

    private fun loadCourses(userId: String) {
        db.collection("Users").document(userId).get()
            .addOnSuccessListener { userDoc ->
                if (!userDoc.exists()) {
                    handleFailure("User document does not exist!")
                    return@addOnSuccessListener
                }

                val user = userDoc.toObject(User::class.java)
                userRole = user?.role ?: "Student"
                userCampus = user?.campus ?: ""

                fabCreateEvent.visibility = if (userRole == "Professor") View.VISIBLE else View.GONE

                val query = if (userRole == "Professor") {
                    db.collection("Courses").whereEqualTo("professorId", userId)
                } else {
                    db.collection("Courses").whereArrayContains("enrolledStudents", userId)
                }

                query.get()
                    .addOnSuccessListener { documents ->
                        allCourses.clear()
                        val courseIds = mutableListOf<String>()
                        for (document in documents) {
                            val course = document.toObject(Course::class.java)
                            allCourses.add(course)
                            course.courseId?.let { courseIds.add(it) }
                        }
                        loadFinals(userId, courseIds)
                    }
                    .addOnFailureListener { e -> handleFailure("Query failed: ${e.message}", e) }
            }
            .addOnFailureListener { e -> handleFailure("Failed to load user: ${e.message}", e) }
    }

    private fun loadFinals(userId: String, courseIds: List<String>) {
        if (courseIds.isEmpty()) {
            loadEvents(userId)
            return
        }

        db.collection("Finals").whereIn("courseId", courseIds).get()
            .addOnSuccessListener { documents ->
                allFinalExams.clear()
                for (document in documents) {
                    val finalExam = document.toObject(FinalExam::class.java)
                    allFinalExams.add(finalExam)
                }
                loadEvents(userId)
            }
            .addOnFailureListener { e -> handleFailure("Error loading finals: ${e.message}", e) }
    }

    private fun loadEvents(userId: String) {
        db.collection("Events").whereArrayContains("participants", userId).get()
            .addOnSuccessListener { documents ->
                allEvents.clear()
                for (document in documents) {
                    val event = document.toObject(Event::class.java)
                    allEvents.add(event)
                }
                progressBar.visibility = View.GONE
                if (viewToggle.isChecked) {
                    renderWeekView()
                } else {
                    filterScheduleByDate()
                }
            }
            .addOnFailureListener { e -> handleFailure("Error loading events: ${e.message}", e) }
    }

    private fun handleFailure(message: String, e: Exception? = null) {
        android.util.Log.e("MySchedule", message, e)
        progressBar.visibility = View.GONE
        if (!viewToggle.isChecked) {
            emptyState.visibility = View.VISIBLE
        }
    }

    private fun filterScheduleByDate() {
        val dayOfWeek = SimpleDateFormat("EEEE", Locale.US).format(selectedDate.time)
        val selectedDateStr = SimpleDateFormat("M/d/yyyy", Locale.US).format(selectedDate.time)
        val dateFormat = SimpleDateFormat("M/d/yyyy", Locale.US)

        val scheduleItems = mutableListOf<ScheduleItem>()

        fun isDateInRange(startDateStr: String, endDateStr: String, selectedDateStr: String): Boolean {
            if (startDateStr.isEmpty() || endDateStr.isEmpty()) return true
            return try {
                val startCal = Calendar.getInstance().apply { time = dateFormat.parse(startDateStr)!! }
                val endCal = Calendar.getInstance().apply { time = dateFormat.parse(endDateStr)!! }
                val selectedCal = Calendar.getInstance().apply { time = dateFormat.parse(selectedDateStr)!! }

                fun Calendar.clearTime() = apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                !selectedCal.clearTime().before(startCal.clearTime()) && !selectedCal.clearTime().after(endCal.clearTime())
            } catch (e: ParseException) {
                android.util.Log.e("MySchedule", "Date parsing failed", e)
                false
            }
        }

        fun stripSeconds(time: String): String {

            return time.replace(":00", "")

        }

        for (course in allCourses) {
            if (isDateInRange(course.semesterStart, course.semesterEnd, selectedDateStr)) {
                for (scheduleMap in course.schedule) {
                    if (scheduleMap["dayOfWeek"]?.equals(dayOfWeek, ignoreCase = true) == true) {
                        scheduleItems.add(ScheduleItem(
                            title = course.courseName,
                            subtitle = course.courseId,
                            startTime = stripSeconds(scheduleMap["startTime"] ?: ""),
                            endTime = stripSeconds(scheduleMap["endTime"] ?: ""),
                            building = scheduleMap["building"] ?: "",
                            room = scheduleMap["room"] ?: ""
                        ))
                    }
                }
            }
        }

        for (finalExam in allFinalExams) {
            if (finalExam.date == selectedDateStr) {
                scheduleItems.add(ScheduleItem(
                    title = "Final Exam: ${finalExam.courseName}",
                    subtitle = finalExam.courseId,
                    startTime = stripSeconds(finalExam.startTime),
                    endTime = stripSeconds(finalExam.endTime),
                    building = finalExam.buildingId,
                    room = finalExam.roomId,
                    isFinalExam = true
                ))
            }
        }

        for (event in allEvents) {
            for (scheduleItem in event.schedule) {
                val isOneTimeEvent = !scheduleItem.recurring && scheduleItem.date.isNotEmpty() && scheduleItem.date == selectedDateStr
                val isRecurringEvent = scheduleItem.recurring && scheduleItem.dayOfWeek.equals(dayOfWeek, ignoreCase = true)

                var inRange = true
                if (isRecurringEvent && event.courseId.isNotEmpty()) {
                    val linkedCourse = allCourses.find { it.courseId == event.courseId }
                    if (linkedCourse != null) {
                        inRange = isDateInRange(linkedCourse.semesterStart, linkedCourse.semesterEnd, selectedDateStr)
                    }
                }

                if (inRange && (isOneTimeEvent || isRecurringEvent)) {
                    val buildingDisplay = if (scheduleItem.building.isNotEmpty()) scheduleItem.building else if (scheduleItem.coordinates.isNotEmpty()) "Custom Location" else ""
                    val roomDisplay = if (scheduleItem.building.isNotEmpty()) scheduleItem.room else ""

                    scheduleItems.add(ScheduleItem(
                        title = event.description,
                        subtitle = "Event by ${event.creator}",
                        startTime = stripSeconds(scheduleItem.startTime),
                        endTime = stripSeconds(scheduleItem.endTime),
                        building = buildingDisplay,
                        room = roomDisplay,
                        isEvent = true
                    ))
                }
            }
        }

        scheduleItems.sortBy { it.startTime }

        progressBar.visibility = View.GONE
        if (scheduleItems.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyState.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            scheduleAdapter.updateSchedule(scheduleItems)
        }
    }

    private fun renderWeekView() {
        dayHeadersContainer.removeAllViews()
        timeColumn.removeAllViews()
        weekColumnsContainer.removeAllViews()

        val weekCalendar = selectedDate.clone() as Calendar
        weekCalendar.set(Calendar.DAY_OF_WEEK, weekCalendar.firstDayOfWeek)
        val columnWidthDp = 120
        val columnWidthPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            columnWidthDp.toFloat(),
            resources.displayMetrics
        ).toInt()

        val headerFormat = SimpleDateFormat("EEE\nM/d", Locale.US)
        for (i in 0 until 7) {
            val headerText = TextView(this)
            headerText.text = headerFormat.format(weekCalendar.time)
            headerText.gravity = Gravity.CENTER
            headerText.textSize = 12f
            headerText.setTextColor(Color.DKGRAY)
            val params = LinearLayout.LayoutParams(columnWidthPx, LinearLayout.LayoutParams.MATCH_PARENT)
            headerText.layoutParams = params
            dayHeadersContainer.addView(headerText)
            weekCalendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        //5:00 AM to 11:00 PM
        val hourHeightDp = 60
        val hourHeightPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            hourHeightDp.toFloat(),
            resources.displayMetrics
        ).toInt()

        for (hour in 5..23) {
            val timeText = TextView(this)
            val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
            val amPm = if (hour < 12) "AM" else "PM"
            timeText.text = String.format("%d %s", displayHour, amPm)
            timeText.gravity = Gravity.CENTER_VERTICAL or Gravity.END
            timeText.textSize = 10f
            timeText.setTextColor(Color.GRAY)
            timeText.setPadding(0, 0, 8, 0)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, hourHeightPx)
            timeText.layoutParams = params
            timeColumn.addView(timeText)
        }

        weekCalendar.time = selectedDate.time
        weekCalendar.set(Calendar.DAY_OF_WEEK, weekCalendar.firstDayOfWeek)
        val dateFormat = SimpleDateFormat("M/d/yyyy", Locale.US)
        fun isDateInRange(startDateStr: String, endDateStr: String, dateStr: String): Boolean {
             if (startDateStr.isEmpty() || endDateStr.isEmpty()) return true
            return try {
                val startCal = Calendar.getInstance().apply { time = dateFormat.parse(startDateStr)!! }
                val endCal = Calendar.getInstance().apply { time = dateFormat.parse(endDateStr)!! }
                val selectedCal = Calendar.getInstance().apply { time = dateFormat.parse(dateStr)!! }
                fun Calendar.clearTime() = apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                !selectedCal.clearTime().before(startCal.clearTime()) && !selectedCal.clearTime().after(endCal.clearTime())
            } catch (e: ParseException) {
                false
            }
        }

        for (i in 0 until 7) {
            val dayLayout = RelativeLayout(this)
            val params = LinearLayout.LayoutParams(columnWidthPx, hourHeightPx * 19)
            if (i > 0) {
                params.leftMargin = 1
            }
            dayLayout.layoutParams = params
            dayLayout.background = resources.getDrawable(R.drawable.day_column_bg, null)
            val currentDateStr = dateFormat.format(weekCalendar.time)
            val currentDayOfWeek = SimpleDateFormat("EEEE", Locale.US).format(weekCalendar.time)
            
            val dayEvents = mutableListOf<BlockInfo>()
            for (course in allCourses) {
                 if (isDateInRange(course.semesterStart, course.semesterEnd, currentDateStr)) {
                    for (scheduleMap in course.schedule) {
                        if (scheduleMap["dayOfWeek"]?.equals(currentDayOfWeek, ignoreCase = true) == true) {
                            val startTime = scheduleMap["startTime"] ?: ""
                            val endTime = scheduleMap["endTime"] ?: ""
                            val details = "Course: ${course.courseName}\nID: ${course.courseId}\nProfessor: ${course.professorName}\nRoom: ${course.room}\nTime: $startTime - $endTime"
                            dayEvents.add(BlockInfo(
                                title = course.courseName,
                                location = course.room,
                                startTime = startTime,
                                endTime = endTime,
                                color = Color.parseColor("#E3F2FD"),
                                details = details
                            ))
                        }
                    }
                 }
            }

            for (finalExam in allFinalExams) {
                if (finalExam.date == currentDateStr) {
                     val details = "Final Exam\nCourse: ${finalExam.courseName}\nRoom: ${finalExam.roomId}\nTime: ${finalExam.startTime} - ${finalExam.endTime}"
                     dayEvents.add(BlockInfo(
                        title = "Final: " + finalExam.courseName,
                        location = finalExam.roomId,
                        startTime = finalExam.startTime,
                        endTime = finalExam.endTime,
                        color = Color.parseColor("#FFEBEE"),
                        details = details
                    ))
                }
            }

            for (event in allEvents) {
                for (scheduleItem in event.schedule) {
                    val isOneTimeEvent = !scheduleItem.recurring && scheduleItem.date.isNotEmpty() && scheduleItem.date == currentDateStr
                    val isRecurringEvent = scheduleItem.recurring && scheduleItem.dayOfWeek.equals(currentDayOfWeek, ignoreCase = true)

                    var inRange = true
                    if (isRecurringEvent && event.courseId.isNotEmpty()) {
                        val linkedCourse = allCourses.find { it.courseId == event.courseId }
                        if (linkedCourse != null) {
                            inRange = isDateInRange(linkedCourse.semesterStart, linkedCourse.semesterEnd, currentDateStr)
                        }
                    }

                    if (inRange && (isOneTimeEvent || isRecurringEvent)) {
                         val loc = if (scheduleItem.building.isNotEmpty()) scheduleItem.room else "Custom"
                         val details = "Event: ${event.description}\nCreator: ${event.creator}\nLocation: $loc\nTime: ${scheduleItem.startTime} - ${scheduleItem.endTime}"
                         dayEvents.add(BlockInfo(
                            title = event.description,
                            location = loc,
                            startTime = scheduleItem.startTime,
                            endTime = scheduleItem.endTime,
                            color = Color.parseColor("#E8F5E9"),
                            details = details
                        ))
                    }
                }
            }
            
            //Find conflicts
            val sortedEvents = dayEvents.sortedBy { parseTimeMinutes(it.startTime) }
            val processedEvents = mutableListOf<BlockInfo>()
            for (event in sortedEvents) {
                var overlapFound = false
                for (processed in processedEvents) {
                    if (isOverlapping(event, processed)) {
                         event.isConflict = true
                         event.conflictWith.add(processed)
                         processed.isConflict = true
                         processed.conflictWith.add(event)
                         overlapFound = true
                    }
                }
                processedEvents.add(event)
            }

            for (event in processedEvents) {
                val finalColor = if (event.isConflict) Color.RED else event.color
                val finalDetails = if (event.isConflict) {
                    val conflictDetails = StringBuilder("CONFLICT DETECTED!\n\n")
                    conflictDetails.append("Event 1:\n${event.details}\n\n")
                    var count = 2
                    for (conflict in event.conflictWith) {
                         conflictDetails.append("Event $count:\n${conflict.details}\n\n")
                         count++
                    }
                    conflictDetails.toString()
                } else {
                    event.details
                }

                addBlockToDay(dayLayout, 
                    event.title, 
                    event.location, 
                    event.startTime, 
                    event.endTime, 
                    finalColor, 
                    hourHeightPx,
                    finalDetails)
            }

            weekColumnsContainer.addView(dayLayout)
            weekCalendar.add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    private fun parseTimeMinutes(timeStr: String): Int {
        try {
            val parser = SimpleDateFormat("h:mm:ss a", Locale.US)
            val cal = Calendar.getInstance()
            try {
                cal.time = parser.parse(timeStr)!!
            } catch (e: Exception) {
                try {
                    cal.time = SimpleDateFormat("h:mm a", Locale.US).parse(timeStr)!!
                } catch (e2: Exception) {
                    return 0
                }
            }
            return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        } catch (e: Exception) {
            return 0
        }
    }

    private fun isOverlapping(event1: BlockInfo, event2: BlockInfo): Boolean {
        val start1 = parseTimeMinutes(event1.startTime)
        val end1 = parseTimeMinutes(event1.endTime)
        val start2 = parseTimeMinutes(event2.startTime)
        val end2 = parseTimeMinutes(event2.endTime)

        return max(start1, start2) < kotlin.math.min(end1, end2)
    }

    private fun addBlockToDay(parent: RelativeLayout, title: String, location: String, startTime: String, endTime: String, color: Int, hourHeightPx: Int, details: String) {
        try {
            val parser = SimpleDateFormat("h:mm:ss a", Locale.US)
            fun parseTime(timeStr: String): Calendar {
                 val cal = Calendar.getInstance()
                 try {
                     cal.time = parser.parse(timeStr)!!
                     return cal
                 } catch (e: Exception) {
                     try {
                         cal.time = SimpleDateFormat("h:mm a", Locale.US).parse(timeStr)!!
                         return cal
                     } catch (e2: Exception) {
                         cal.set(Calendar.HOUR_OF_DAY, 0)
                         cal.set(Calendar.MINUTE, 0)
                         return cal
                     }
                 }
            }

            val startCal = parseTime(startTime)
            val endCal = parseTime(endTime)

            val startHour = startCal.get(Calendar.HOUR_OF_DAY)
            val startMinute = startCal.get(Calendar.MINUTE)
            
            val endHour = endCal.get(Calendar.HOUR_OF_DAY)
            val endMinute = endCal.get(Calendar.MINUTE)

            val minutesFromMidnight = startHour * 60 + startMinute
            val durationMinutes = (endHour * 60 + endMinute) - minutesFromMidnight
            
            if (durationMinutes <= 0) return
            
            val pixelsPerMinute = hourHeightPx / 60f
            val topMargin = ((minutesFromMidnight - 300) * pixelsPerMinute).toInt()
            val height = (durationMinutes * pixelsPerMinute).toInt()

            val card = CardView(this)
            val params = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, height)
            params.topMargin = topMargin
            params.marginStart = 2
            params.marginEnd = 2
            card.layoutParams = params
            card.setCardBackgroundColor(color)
            card.radius = 8f
            card.cardElevation = 4f
            card.isClickable = true
            card.isFocusable = true
            card.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle(if (color == Color.RED) "Schedule Conflict" else title)
                    .setMessage(details)
                    .setPositiveButton("OK", null)
                    .show()
            }

            val container = LinearLayout(this)
            container.orientation = LinearLayout.VERTICAL
            container.setPadding(4, 4, 4, 4)
            
            val titleView = TextView(this)
            titleView.text = title
            titleView.textSize = 10f
            titleView.setTypeface(null, android.graphics.Typeface.BOLD)
            titleView.maxLines = 2
            titleView.ellipsize = android.text.TextUtils.TruncateAt.END
            
            val locView = TextView(this)
            locView.text = location
            locView.textSize = 8f
            container.addView(titleView)
            container.addView(locView)
            card.addView(container)
            parent.addView(card)

        } catch (e: Exception) {
            android.util.Log.e("WeekView", "Error adding block: ${e.message}")
        }
    }

    private fun showEventDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_event, null)
        val eventNameInput = dialogView.findViewById<EditText>(R.id.etEventName)
        val eventLocationInput = dialogView.findViewById<EditText>(R.id.etEventLocation)
        val eventDateInput = dialogView.findViewById<EditText>(R.id.etEventDate)
        val eventTimeInput = dialogView.findViewById<EditText>(R.id.etEventTime)
        val eventDescriptionInput = dialogView.findViewById<EditText>(R.id.etEventDescription)
        val courseSpinner = dialogView.findViewById<Spinner>(R.id.spinnerEventCourse)

        loadCoursesForSpinner(courseSpinner)

        val calendar = Calendar.getInstance()
        var selectedDateStr = ""
        var selectedTime = ""

        eventDateInput.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    selectedDateStr = "${month + 1}/$day/$year"
                    eventDateInput.setText(selectedDateStr)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        eventTimeInput.setOnClickListener {
            TimePickerDialog(
                this,
                { _, hour, minute ->
                    val amPm = if (hour >= 12) "PM" else "AM"
                    val displayHour = if (hour > 12) hour - 12 else if (hour == 0) 12 else hour
                    selectedTime = String.format("%d:%02d %s", displayHour, minute, amPm)
                    eventTimeInput.setText(selectedTime)
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                false
            ).show()
        }

        AlertDialog.Builder(this)
            .setTitle("Create Event")
            .setView(dialogView)
            .setPositiveButton("Create") { _, _ ->
                val name = eventNameInput.text.toString()
                val location = eventLocationInput.text.toString()
                val description = eventDescriptionInput.text.toString()
                val date = selectedDateStr
                val time = selectedTime
                val selectedCourseItem = courseSpinner.selectedItem as? CourseSpinnerItem
                val courseId = selectedCourseItem?.courseId ?: ""

                if (name.isNotBlank() && date.isNotBlank() && time.isNotBlank()) {
                    saveEventToFirebase(name, description, location, date, time, courseId)
                } else {
                    Toast.makeText(this, "Please fill required fields", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadCoursesForSpinner(spinner: Spinner) {
        val courseItems = mutableListOf<CourseSpinnerItem>()
        courseItems.add(CourseSpinnerItem("", "No Course"))

        for (course in allCourses) {
            courseItems.add(CourseSpinnerItem(course.courseId ?: "", "${course.courseId} - ${course.courseName}"))
        }

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            courseItems
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }

    private fun saveEventToFirebase(
        name: String,
        description: String,
        location: String,
        dateStr: String,
        timeStr: String,
        courseId: String
    ) {
        val userId = auth.currentUser?.uid ?: return
        progressBar.visibility = View.VISIBLE

        db.collection("Users").document(userId).get()
            .addOnSuccessListener { userDoc ->
                val user = userDoc.toObject(User::class.java)
                val creatorName = "${user?.firstName} ${user?.lastName}"

                val participants = mutableListOf(userId)

                if (courseId.isNotEmpty()) {
                    val linkedCourse = allCourses.find { it.courseId == courseId }
                    linkedCourse?.enrolledStudents?.let { participants.addAll(it) }
                }

                var startTime = timeStr
                var endTime = ""
                try {
                    val df = SimpleDateFormat("h:mm a", Locale.US)
                    val date = df.parse(timeStr)
                    val cal = Calendar.getInstance()
                    if (date != null) {
                        cal.time = date
                        cal.add(Calendar.HOUR_OF_DAY, 1)
                        endTime = df.format(cal.time)
                    }
                } catch (e: Exception) {
                    endTime = timeStr
                }

                val scheduleItem = EventSchedule(
                    building = location,
                    coordinates = "",
                    date = dateStr,
                    dayOfWeek = "", 
                    endTime = endTime,
                    startTime = startTime,
                    recurring = false
                )

                val newEvent = Event(
                    creator = creatorName,
                    description = name, 
                    participants = participants,
                    private = courseId.isEmpty(),
                    schedule = listOf(scheduleItem),
                    courseId = courseId
                )

                db.collection("Events").add(newEvent)
                    .addOnSuccessListener {
                        progressBar.visibility = View.GONE
                        Toast.makeText(this, "Event Created", Toast.LENGTH_SHORT).show()
                        loadEvents(userId)
                    }
                    .addOnFailureListener { e ->
                        progressBar.visibility = View.GONE
                        Toast.makeText(this, "Error creating event: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                 progressBar.visibility = View.GONE
                 Toast.makeText(this, "Error fetching user: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}

data class BlockInfo(
    val title: String,
    val location: String,
    val startTime: String,
    val endTime: String,
    val color: Int,
    val details: String,
    var isConflict: Boolean = false,
    var conflictWith: MutableList<BlockInfo> = mutableListOf()
)

data class Event(
    val creator: String = "",
    val description: String = "",
    val participants: List<String> = listOf(),
    val private: Boolean = false,
    val schedule: List<EventSchedule> = listOf(),
    val courseId: String = ""
)

data class EventSchedule(
    val building: String = "",
    val coordinates: String = "",
    val date: String = "",
    val dayOfWeek: String = "",
    val endTime: String = "",
    val recurring: Boolean = false,
    val room: String = "",
    val startTime: String = ""
)

data class FinalExam(
    val buildingId: String = "",
    val roomId: String = "",
    val courseId: String = "",
    val courseName: String = "",
    val date: String = "",
    val startTime: String = "",
    val endTime: String = ""
)

data class ScheduleItem(
    val title: String,
    val subtitle: String,
    val startTime: String,
    val endTime: String,
    val building: String,
    val room: String,
    val isFinalExam: Boolean = false,
    val isEvent: Boolean = false
)

data class CourseSpinnerItem(val courseId: String, val displayText: String) {
    override fun toString(): String {
        return displayText
    }
}
