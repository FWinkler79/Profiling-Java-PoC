#!/bin/bash

# Load Generator for jfr-profiler-based-app
# Generates various types of load to observe snapshot size changes

ENDPOINT_LIGHT="http://localhost:8080/api/hello"
ENDPOINT_HEAVY="http://localhost:8080/api/heavy"

echo "="
echo "JFR Test Application - Load Generator"
echo "="
echo ""
echo "Select load pattern:"
echo "  1) Light load  - 10 requests/second to /api/hello"
echo "  2) Medium load - 5 requests/second mixed endpoints"
echo "  3) Heavy load  - Continuous /api/heavy requests"
echo "  4) Burst load  - Alternating idle and busy periods"
echo "  5) Custom     - Manual configuration"
echo ""
read -p "Enter choice (1-5): " choice

case $choice in
    1)
        echo "Starting light load (10 req/s to /api/hello)..."
        echo "Press Ctrl+C to stop"
        while true; do
            curl -s "$ENDPOINT_LIGHT?name=User$RANDOM" > /dev/null &
            sleep 0.1
        done
        ;;

    2)
        echo "Starting medium load (mixed endpoints)..."
        echo "Press Ctrl+C to stop"
        count=0
        while true; do
            if [ $((count % 5)) -eq 0 ]; then
                curl -s "$ENDPOINT_HEAVY" > /dev/null &
            else
                curl -s "$ENDPOINT_LIGHT?name=User$RANDOM" > /dev/null &
            fi
            count=$((count + 1))
            sleep 0.2
        done
        ;;

    3)
        echo "Starting heavy load (continuous /api/heavy)..."
        echo "Press Ctrl+C to stop"
        while true; do
            curl -s "$ENDPOINT_HEAVY" > /dev/null &
            sleep 0.5
        done
        ;;

    4)
        echo "Starting burst load (10s busy, 10s idle)..."
        echo "Press Ctrl+C to stop"
        while true; do
            echo "Burst phase - generating load..."
            end=$((SECONDS+10))
            while [ $SECONDS -lt $end ]; do
                curl -s "$ENDPOINT_LIGHT?name=User$RANDOM" > /dev/null &
                curl -s "$ENDPOINT_HEAVY" > /dev/null &
                sleep 0.1
            done

            echo "Idle phase - no requests..."
            sleep 10
        done
        ;;

    5)
        echo ""
        read -p "Endpoint (light/heavy): " ep
        read -p "Requests per second: " rps

        if [ "$ep" = "heavy" ]; then
            endpoint=$ENDPOINT_HEAVY
        else
            endpoint=$ENDPOINT_LIGHT
        fi

        delay=$(echo "scale=3; 1/$rps" | bc)

        echo "Starting custom load ($rps req/s to $endpoint)..."
        echo "Press Ctrl+C to stop"
        while true; do
            curl -s "$endpoint?name=User$RANDOM" > /dev/null &
            sleep $delay
        done
        ;;

    *)
        echo "Invalid choice"
        exit 1
        ;;
esac
