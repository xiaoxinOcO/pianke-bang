const { get } = require('../utils/http')

function fetchHallActivities() {
  return get('/hall-activities')
}

module.exports = {
  fetchHallActivities
}
