package com.example.coolconnect

object AddressData {
    val regions = listOf("NCR (Metro Manila)")

    val provinces = mapOf(
        "NCR (Metro Manila)" to listOf("Metro Manila")
    )

    val cities = mapOf(
        "Metro Manila" to listOf(
            "Caloocan", "Las Piñas", "Makati", "Malabon", "Mandaluyong", "Manila", 
            "Marikina", "Muntinlupa", "Navotas", "Parañaque", "Pasay", "Pasig", 
            "Pateros", "Quezon City", "San Juan", "Taguig", "Valenzuela"
        )
    )

    val barangays = mapOf(
        "Caloocan" to (1..188).map { "Barangay $it" },
        "Las Piñas" to listOf("Almanza Uno", "Almanza Dos", "CAA-BF International", "Daniel Fajardo", "Elias Aldana", "Ilaya", "Manuyo Uno", "Manuyo Dos", "Pamplona Uno", "Pamplona Dos", "Pamplona Tres", "Pilar Village", "Pulang Lupa Uno", "Pulang Lupa Dos", "Zapote", "Talon Uno", "Talon Dos", "Talon Tres", "Talon Cuatro", "Talon Singko"),
        "Makati" to listOf("Bangkal", "Bel-Air", "Carmona", "Cembo", "Comembo", "Dasmariñas", "Forbes Park", "Guadalupe Nuevo", "Guadalupe Viejo", "Kasilawan", "La Paz", "Magallanes", "Olympia", "Palanan", "Pembo", "Poblacion", "Post Proper North", "Post Proper South", "Rizal", "San Antonio", "San Lorenzo", "Santa Cruz", "Singkamas", "South Cembo", "Tejeros", "Urdaneta", "Valenzuela", "West Rembo"),
        "Malabon" to listOf("Acacia", "Baritan", "Bayan-bayanan", "Catmon", "Concepcion", "Dampalit", "Flores", "Hulong Duhat", "Ibaba", "Longos", "Maysilo", "Muzon", "Niugan", "Panghulo", "Potrero", "San Agustin", "Santolan", "Tañong", "Tinajeros", "Tonsuya", "Tugatog"),
        "Mandaluyong" to listOf("Addition Hills", "Bagong Silang", "Barangka Drive", "Barangka Ibaba", "Barangka Itaas", "Barangka Ilaya", "Burol", "Daang Bakal", "Hagdang Bato Itaas", "Hagdang Bato Libis", "Harapin Ang Bukas", "Highway Hills", "Hulo", "Mabini-J. Rizal", "Malamig", "Namayan", "New Zañiga", "Old Zañiga", "Pag-asa", "Plainview", "Pleasant Hills", "Poblacion", "San Jose", "Vergara", "Wack-Wack Greenhills"),
        "Manila" to listOf("Binondo", "Ermita", "Intramuros", "Malate", "Paco", "Pandacan", "Port Area", "Quiapo", "Sampaloc", "San Andres", "San Miguel", "San Nicolas", "Santa Ana", "Santa Cruz", "Santa Mesa", "Tondo 1", "Tondo 2"),
        "Marikina" to listOf("Barangka", "Calumpang", "Concepcion Uno", "Concepcion Dos", "Fortune", "Industrial Valley", "Jesus Dela Peña", "Malanday", "Marikina Heights", "Nangka", "Parang", "Poblacion", "San Roque", "Santa Elena", "Sto. Niño", "Taňong", "Tumana"),
        "Muntinlupa" to listOf("Alabang", "Bayanan", "Buli", "Cupang", "Poblacion", "Putatan", "Sucat", "Tunasan", "Ayala Alabang"),
        "Navotas" to listOf("Bagumbayan North", "Bagumbayan South", "Bangkulasi", "Daanghari", "Navotas East", "Navotas West", "NBBS Dagat-dagatan", "NBBS Kaunlaran", "NBBS Proper", "North Bay Blvd North", "North Bay Blvd South", "San Jose", "San Roque", "Tangos North", "Tangos South", "Tanza 1", "Tanza 2"),
        "Parañaque" to listOf("Baclaran", "Don Galo", "La Huerta", "Sto. Niño", "Tambo", "Vitalez", "San Dionisio", "San Isidro", "BF Homes", "Don Bosco", "Marcelo Green", "Merville", "Moonwalk", "Sun Valley", "San Antonio", "San Martin de Porres"),
        "Pasay" to (1..201).map { "Barangay $it" },
        "Pasig" to listOf("Bagong Ilog", "Bagong Katipunan", "Bambang", "Buting", "Caniogan", "Dela Paz", "Kapasigan", "Kapitolyo", "Malinao", "Manggahan", "Maybunga", "Oranbo", "Pineda", "Pinagbuhatan", "Rosario", "San Antonio", "San Joaquin", "San Miguel", "San Nicolas", "Santolan", "Sta. Cruz", "Sta. Lucia", "Sumilang", "Ugong"),
        "Pateros" to listOf("Aguho", "Magtanggol", "Martirez Del 96", "Poblacion", "San Pedro", "San Roque", "Santa Ana", "Santo Rosario-Kanluran", "Santo Rosario-Silangan", "Tabacalera"),
        "Quezon City" to listOf("Alicia", "Amihan", "Apolonio Samson", "Baesa", "Bagbag", "Bagong Lipunan ng Crame", "Bagong Pag-asa", "Bagong Silangan", "Bagumbayan", "Bagumbuhay", "Bahay Toro", "Balingasa", "Balong Bato", "Batasan Hills", "Bayanihan", "Blue Ridge A", "Blue Ridge B", "Botocan", "Bungad", "Camp Aguinaldo", "Capri", "Central", "Claro", "Commonwealth", "Concepcion", "Culiat", "Damar", "Damayan", "Damayan Lagi", "Duyan-duyan", "E. Rodriguez", "East Kamias", "Escopa I", "Escopa II", "Escopa III", "Escopa IV", "Fairview", "Greater Lagro", "Gulod", "Holy Spirit", "Horse Shoe", "Immaculate Concepcion", "Kaligayahan", "Kalusugan", "Kamuning", "Katipunan", "Kaunlaran", "Kristong Hari", "Krus na Ligas", "Laging Handa", "Libis", "Lourdes", "Loyola Heights", "Maharlika", "Malaya", "Mangga", "Manresa", "Mariana", "Mariblo", "Marilag", "Masagana", "Masambong", "Matandang Balara", "Milagrosa", "N.S. Amoranto", "Nagkaisang Nayon", "Nayong Kanluran", "New Era", "North Fairview", "Novaliches Proper", "Obrero", "Old Balara", "Paang Bundok", "Pag-ibig sa Nayon", "Paligsahan", "Paltok", "Pansol", "Paraiso", "Pasong Putik Proper", "Pasong Tamo", "Payatas", "Phil-Am", "Pinagkaisahan", "Pinyahan", "Project 6", "Quirino 2-A", "Quirino 2-B", "Quirino 2-C", "Quirino 3-A", "Ramylu Village", " Roxas", "Sacred Heart", "Salvacion", "San Agustin", "San Antonio", "San Bartolome", "San Isidro", "San Isidro Labrador", "San Jose", "San Martin de Porres", "San Roque", "San Vicente", "Santa Cruz", "Santa Lucia", "Santa Monica", "Santa Teresita", "Santo Cristo", "Santo Niño", "Santol", "Sauyo", "Sienna", "Sikatuna Village", "Silangan", "Socorro", "South Triangle", "St. Ignatius", "St. Peter", "Stella Maris", "Sto. Domingo", "Tagumpay", "Talayan", "Talipapa", "Tandang Sora", "Tatalon", "Teachers Village East", "Teachers Village West", "U.P. Campus", "U.P. Village", "Ugong Norte", "Unang Sigaw", "Valencia", "Vasra", "Veterans Village", "Villa Maria Clara", "West Kamias", "West Triangle", "White Plains"),
        "San Juan" to listOf("Addition Hills", "Balong-Bato", "Batis", "Corazon de Jesus", "Ermitaño", "Greenhills", "Isabelita", "Kabayanan", "Little Baguio", "Maytunas", "Onse", "Pasadena", "Pedro Cruz", "Progreso", "Rivera", "Salapan", "San Perfecto", "St. Joseph", "Sta. Lucia", "Tibagan", "West Crame"),
        "Taguig" to listOf("Bagumbayan", "Bambang", "Calzada", "Central Bicutan", "Central Signal Village", "Fort Bonifacio", "Hagonoy", "Ibayo-Tipas", "Katuparan", "Ligid-Tipas", "Lower Bicutan", "Maharlika Village", "Napindan", "New Lower Bicutan", "North Daang Hari", "North Signal Village", "Palingon", "Pinagsama", "San Roque", "Santa Ana", "South Daang Hari", "South Signal Village", "Tanyag", "Tuktukan", "Upper Bicutan", "Ususan", "Wawa", "Western Bicutan"),
        "Valenzuela" to listOf("Arkong Bato", "Bagbaguin", "Balangkas", "Bignay", "Bisig", "Canumay East", "Canumay West", "Coloong", "Dalandanan", "Gen. T. de Leon", "Isla", "Karuhatan", "Lawang Bato", "Lingunan", "Mabolo", "Malanday", "Malinta", "Mapulang Lupa", "Marulas", "Maysan", "Palasan", "Pariancillo Villa", "Pasolo", "Paso de Blas", "Poblacion", "Pulo", "Punturin", "Rincon", "Tagalag", "Ugong", "Viente Reales", "Wawang Pulo")
    )
}
